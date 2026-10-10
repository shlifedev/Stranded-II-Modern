import Foundation
import CryptoKit

@main
struct RuntimeTests {
    static func main() throws {
        func check(_ condition: Bool, line: UInt = #line) { precondition(condition, "Check failed at line \(line)") }
        let fm = FileManager.default
        let root = fm.temporaryDirectory.appendingPathComponent("Stranded II 한글 \(UUID().uuidString)")
        defer { try? fm.removeItem(at: root) }
        let resources = root.appendingPathComponent("Moved App/Contents/Resources")
        let game = resources.appendingPathComponent("game")
        let dataRoot = root.appendingPathComponent("Application Support/Stranded II")
        try fm.createDirectory(at: game, withIntermediateDirectories: true)
        func write(_ path: String, _ text: String, in base: URL) throws {
            let url = base.appendingPathComponent(path)
            try fm.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
            try Data(text.utf8).write(to: url)
        }
        func read(_ path: String, in base: URL) throws -> String {
            try String(contentsOf: base.appendingPathComponent(path), encoding: .utf8)
        }
        func manifest(_ id: String, _ names: [String]) throws {
            var files: [String: String] = [:]
            for name in names {
                files[name] = SHA256.hash(data: try Data(contentsOf: game.appendingPathComponent(name)))
                    .map { String(format: "%02x", $0) }.joined()
            }
            try JSONEncoder().encode(Payload(id: id, files: files)).write(to: resources.appendingPathComponent("payload.json"))
        }
        var lock: RuntimeLock? = try RuntimeLock(root: dataRoot)
        do { _ = try RuntimeLock(root: dataRoot); fatalError("Second lock succeeded") }
        catch { assert(error.localizedDescription.contains("already running")) }
        let names = ["StrandedII.exe", "core/modern.ini", "mods/Stranded II/keys.cfg", "obsolete.dat", "replaced.dat"]
        for name in names { try write(name, "version one", in: game) }
        try manifest("one", names)
        let first = try prepareGame(resources: resources, root: dataRoot)
        try mapGameDrive(game: first, root: dataRoot)
        check(dataRoot.appendingPathComponent("prefix/dosdevices/s:").resolvingSymlinksInPath().path == first.resolvingSymlinksInPath().path)
        check(try read("core/modern.ini", in: first) == "version one")
        try write("core/modern.ini", "language=ko\nfps=120", in: first)
        try write("mods/Stranded II/keys.cfg", "custom keys", in: first)
        try write("mods/Stranded II/saves/slot.sav", "saved world", in: first)
        try write("custom/map.s2", "custom map", in: first)
        try fm.removeItem(at: first.appendingPathComponent("replaced.dat"))
        try write("replaced.dat/user-map.s2", "user directory", in: first)
        check(try prepareGame(resources: resources, root: dataRoot).path == first.path)
        for name in names { try write(name, "version two", in: game) }
        try manifest("two", Array(names.dropLast(2)))
        let second = try prepareGame(resources: resources, root: dataRoot)
        try mapGameDrive(game: second, root: dataRoot)
        check(dataRoot.appendingPathComponent("prefix/dosdevices/s:").resolvingSymlinksInPath().path == second.resolvingSymlinksInPath().path)
        assert(second != first)
        check(try read("StrandedII.exe", in: second) == "version two")
        check(try read("core/modern.ini", in: second) == "language=ko\nfps=120")
        check(try read("mods/Stranded II/keys.cfg", in: second) == "custom keys")
        check(try read("mods/Stranded II/saves/slot.sav", in: second) == "saved world")
        check(try read("custom/map.s2", in: second) == "custom map")
        check(try read("replaced.dat/user-map.s2", in: second) == "user directory")
        assert(!fm.fileExists(atPath: second.appendingPathComponent("obsolete.dat").path))
        assert(fm.fileExists(atPath: first.appendingPathComponent("obsolete.dat").path))
        try manifest("three", Array(names.dropLast(2)))
        try write("StrandedII.exe", "corruption", in: game)
        do { _ = try prepareGame(resources: resources, root: dataRoot); fatalError("Corrupt update succeeded") }
        catch { assert(error.localizedDescription.contains("Damaged")) }
        let target = dataRoot.appendingPathComponent("current").resolvingSymlinksInPath()
        assert(target == second.resolvingSymlinksInPath())
        check(try fm.contentsOfDirectory(atPath: dataRoot.appendingPathComponent("generations").path).count == 2)
        try write("StrandedII.exe", "version three", in: game)
        try manifest("three", Array(names.dropLast(2)))
        try fm.removeItem(at: second.appendingPathComponent("core/modern.ini"))
        try fm.createSymbolicLink(at: second.appendingPathComponent("core/modern.ini"), withDestinationURL: game.appendingPathComponent("StrandedII.exe"))
        do { _ = try prepareGame(resources: resources, root: dataRoot); fatalError("Symlink update succeeded") }
        catch { assert(error.localizedDescription.contains("outside")) }
        check(try read("StrandedII.exe", in: game) == "version three")
        withExtendedLifetime(lock) {}
        lock = nil
        _ = try RuntimeLock(root: dataRoot)
        print("Native runtime: install, lock, update, user data, rollback and Unicode paths passed")
    }
}
