import AppKit
import Foundation

@main
struct Launcher {
    static func execute(_ executable: URL, _ arguments: [String], directory: URL,
                        environment: [String: String], log: FileHandle) throws {
        let process = Process()
        process.executableURL = executable
        process.arguments = arguments
        process.currentDirectoryURL = directory
        process.environment = environment
        process.standardOutput = log
        process.standardError = log
        try process.run()
        process.waitUntilExit()
        guard process.terminationStatus == 0 else {
            throw LaunchError.message("\(executable.lastPathComponent) exited with status \(process.terminationStatus). See launcher.log in the game's Application Support folder.")
        }
    }

    static func main() {
        let args = Array(CommandLine.arguments.dropFirst())
        let smoke = args.first == "--smoke-test"
        do {
            guard args.isEmpty || (smoke && args.count == 2 && args[1].hasPrefix("/")) else {
                throw LaunchError.message("Usage: StrandedII [--smoke-test /absolute/temporary-data-directory]")
            }
            let fm = FileManager.default
            let root: URL
            if smoke {
                root = URL(fileURLWithPath: args[1], isDirectory: true)
                guard !fm.fileExists(atPath: root.path) else { throw LaunchError.message("Smoke tests require a new data directory.") }
            } else {
                root = try fm.url(for: .applicationSupportDirectory, in: .userDomainMask,
                                  appropriateFor: nil, create: true).appendingPathComponent("Stranded II")
            }
            guard let resources = Bundle.main.resourceURL else { throw LaunchError.message("Missing app resources.") }
            let lock = try RuntimeLock(root: root)
            try withExtendedLifetime(lock) {
                let logURL = root.appendingPathComponent("launcher.log")
                if !fm.fileExists(atPath: logURL.path) { fm.createFile(atPath: logURL.path, contents: nil) }
                let log = try FileHandle(forWritingTo: logURL)
                defer { try? log.close() }
                try log.seekToEnd()
                let engine = resources.appendingPathComponent("wine")
                var env = ProcessInfo.processInfo.environment
                // Ignore a shell's Wine/bottle/DYLD configuration.
                for key in env.keys.filter({ $0.hasPrefix("WINE") || $0.hasPrefix("DYLD_") }) { env.removeValue(forKey: key) }
                env["WINEPREFIX"] = root.appendingPathComponent("prefix").path
                env["WINEARCH"] = "win64"
                env["WINEBOOT_HIDE_DIALOG"] = "1"
                env["WINEDEBUG"] = "-all,err+all"
                env["WINEDLLOVERRIDES"] = "winemenubuilder.exe,mscoree,mshtml,winedmo,winegstreamer=d;ddraw,d3d8,d3d9,wined3d=b"
                env["DYLD_LIBRARY_PATH"] = engine.appendingPathComponent("lib").path
                env["DYLD_FALLBACK_LIBRARY_PATH"] = engine.appendingPathComponent("lib").path + ":/usr/lib"
                env["PATH"] = engine.appendingPathComponent("bin").path + ":/usr/bin:/bin:/usr/sbin:/sbin"
                do {
                    try execute(URL(fileURLWithPath: "/usr/bin/arch"), ["-x86_64", "/usr/bin/true"], directory: root, environment: env, log: log)
                } catch { throw LaunchError.message("Rosetta 2 is required. Install Rosetta using macOS, then open Stranded II again.") }
                let game = try prepareGame(resources: resources, root: root)
                let wine = engine.appendingPathComponent("bin/wine")
                defer { try? execute(engine.appendingPathComponent("bin/wineserver"), ["-w"], directory: game, environment: env, log: log) }
                // Running a console command also initializes a fresh prefix.
                try execute(wine, ["cmd", "/c", "echo Stranded II Wine ready"], directory: game, environment: env, log: log)
                try mapGameDrive(game: game, root: root)
                if smoke {
                    try execute(wine, ["C:\\windows\\syswow64\\cmd.exe", "/c", "echo Stranded II 32-bit Wine ready"], directory: game, environment: env, log: log)
                    try execute(wine, ["cmd", "/c", "if not exist S:\\StrandedII.exe exit /b 1"], directory: game, environment: env, log: log)
                    try execute(wine, ["cmd", "/c", "cd"], directory: game, environment: env, log: log)
                }
                try execute(wine, ["reg", "add", "HKCU\\Software\\Wine\\Direct3D", "/v", "renderer", "/d", "gl", "/f"], directory: game, environment: env, log: log)
                if smoke {
                    // No game is running in this newly created test prefix.
                    // Stop background services rather than waiting for idle devices.
                    try execute(engine.appendingPathComponent("bin/wineserver"), ["-k"], directory: game, environment: env, log: log)
                } else { try execute(wine, ["S:\\StrandedII.exe"], directory: game, environment: env, log: log) }
            }
        } catch {
            let message = error.localizedDescription
            if smoke { fputs(message + "\n", stderr) }
            else {
                NSApplication.shared.setActivationPolicy(.accessory)
                NSApplication.shared.activate(ignoringOtherApps: true)
                let alert = NSAlert()
                alert.messageText = "Stranded II"
                alert.informativeText = message
                alert.alertStyle = .warning
                alert.runModal()
            }
            exit(1)
        }
    }
}
