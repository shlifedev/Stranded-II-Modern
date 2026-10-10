import Foundation
import CryptoKit
import Darwin

enum LaunchError: LocalizedError {
    case message(String)
    var errorDescription: String? {
        switch self { case .message(let text): return text }
    }
}

struct Payload: Codable {
    let id: String
    let files: [String: String]
}

// The same rules as the Windows packager, plus all configurable key bindings.
func playerFile(_ path: String) -> Bool {
    let parts = path.lowercased().split(separator: "/").map(String.init)
    let name = parts.last ?? ""
    return parts.contains("saves") || parts.contains("preserved") ||
        ["settings.inf", "modern.ini"].contains(name) || name.hasPrefix("screen_") ||
        ["cfg", "sav", "bak", "tmp", "log"].contains((name as NSString).pathExtension)
}

func checkedPath(_ name: String, in root: URL) throws -> URL {
    let parts = name.split(separator: "/", omittingEmptySubsequences: false)
    guard !parts.isEmpty, parts.allSatisfy({ !$0.isEmpty && $0 != "." && $0 != ".." && !$0.contains("\\") && !$0.contains(":") }) else {
        throw LaunchError.message("Invalid game file path: \(name)")
    }
    var result = root
    for part in parts {
        result.appendPathComponent(String(part))
        if (try? FileManager.default.attributesOfItem(atPath: result.path)[.type]) as? FileAttributeType == .typeSymbolicLink {
            throw LaunchError.message("A game file points outside its directory: \(name)")
        }
    }
    return result
}

final class RuntimeLock {
    private let descriptor: Int32
    init(root: URL) throws {
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        descriptor = open(root.appendingPathComponent("launcher.lock").path, O_CREAT | O_RDWR | O_CLOEXEC, 0o600)
        guard descriptor >= 0 else { throw LaunchError.message("Cannot open the game lock.") }
        if flock(descriptor, LOCK_EX | LOCK_NB) != 0 {
            close(descriptor)
            throw LaunchError.message("Stranded II is already running or updating.")
        }
    }
    deinit { flock(descriptor, LOCK_UN); close(descriptor) }
}

func mapGameDrive(game: URL, root: URL) throws {
    // Blitz uses ANSI CurrentDir(). Give it an ASCII Windows path even when
    // the macOS home directory contains characters outside Windows-1252.
    let fm = FileManager.default
    let devices = root.appendingPathComponent("prefix/dosdevices")
    try fm.createDirectory(at: devices, withIntermediateDirectories: true)
    let drive = devices.appendingPathComponent("s:")
    if let type = try? fm.attributesOfItem(atPath: drive.path)[.type] as? FileAttributeType {
        guard type == .typeSymbolicLink else { throw LaunchError.message("The game's private S: drive is occupied by a file or directory.") }
        try fm.removeItem(at: drive)
    }
    try fm.createSymbolicLink(at: drive, withDestinationURL: game)
}

func prepareGame(resources: URL, root: URL) throws -> URL {
    let fm = FileManager.default
    let payloadData = try Data(contentsOf: resources.appendingPathComponent("payload.json"))
    let payload = try JSONDecoder().decode(Payload.self, from: payloadData)
    let current = root.appendingPathComponent("current")
    let generations = root.appendingPathComponent("generations")
    try fm.createDirectory(at: generations, withIntermediateDirectories: true)
    var previous: URL?
    var oldFiles: [String: String] = [:]
    if (try? fm.attributesOfItem(atPath: current.path)) != nil {
        let link = try fm.destinationOfSymbolicLink(atPath: current.path)
        guard link.hasPrefix("generations/"), link.split(separator: "/").count == 2,
              !link.contains("..") else { throw LaunchError.message("Invalid current game directory.") }
        let old = root.appendingPathComponent(link)
        guard old.resolvingSymlinksInPath().deletingLastPathComponent() == generations.resolvingSymlinksInPath() else {
            throw LaunchError.message("Game directory escapes Application Support.")
        }
        let oldPayload = try JSONDecoder().decode(Payload.self, from: Data(contentsOf: old.appendingPathComponent(".payload.json")))
        if oldPayload.id == payload.id { return old }
        oldFiles = oldPayload.files
        previous = old
    }
    // Publish a new generation with a single rename of the current symlink.
    // Old generations are retained as recovery copies; no saves are deleted.
    let name = UUID().uuidString
    let stage = generations.appendingPathComponent(name)
    let next = root.appendingPathComponent(".current-\(name)")
    var committed = false
    defer {
        if !committed { try? fm.removeItem(at: stage) }
        try? fm.removeItem(at: next)
    }
    if let previous { try fm.copyItem(at: previous, to: stage) }
    else { try fm.createDirectory(at: stage, withIntermediateDirectories: true) }
    for file in oldFiles.keys where payload.files[file] == nil && !playerFile(file) {
        let path = try checkedPath(file, in: stage)
        // A user may have replaced an old resource with a directory of their
        // own files. Only remove regular files recorded by the previous build.
        if (try? fm.attributesOfItem(atPath: path.path)[.type]) as? FileAttributeType == .typeRegular {
            try fm.removeItem(at: path)
        }
    }
    for (file, expected) in payload.files {
        let source = try checkedPath(file, in: resources.appendingPathComponent("game"))
        let data = try Data(contentsOf: source)
        let actual = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
        guard actual == expected else { throw LaunchError.message("Damaged game resource: \(file). Download the app again.") }
        let target = try checkedPath(file, in: stage)
        if playerFile(file) && fm.fileExists(atPath: target.path) { continue }
        try fm.createDirectory(at: target.deletingLastPathComponent(), withIntermediateDirectories: true)
        try data.write(to: target, options: .atomic)
    }
    try fm.createDirectory(at: stage.appendingPathComponent("mods/Stranded II/saves"), withIntermediateDirectories: true)
    try payloadData.write(to: stage.appendingPathComponent(".payload.json"), options: .atomic)
    try fm.createSymbolicLink(atPath: next.path, withDestinationPath: "generations/\(name)")
    guard rename(next.path, current.path) == 0 else { throw LaunchError.message("Cannot activate the updated game.") }
    committed = true
    return stage
}
