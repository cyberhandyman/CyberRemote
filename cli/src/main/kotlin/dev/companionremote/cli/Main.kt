package dev.companionremote.cli

import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

const val USAGE = """companion-remote cli — Apple TV Companion protocol harness

Usage: cli <command> [options]

Commands:
  scan                          discover Apple TVs (_companion-link._tcp)
  pair        --host <ip>       pair (PIN appears on the TV)
  verify      --host <ip>       connect + pair-verify with stored credentials
  command <name> --host <ip>    press a button (up|down|left|right|select|
                                menu|home|play_pause|volume_up|volume_down|
                                siri|screensaver|sleep|wake|channel_up|
                                channel_down|guide|page_up|page_down)
  apps        --host <ip>       list launchable apps
  launch <bundle-id> --host     launch an app
  power       --host <ip>       show power state (attention state)
  text-set <text>  --host <ip>  replace text in the focused field
  text-append <text> --host     append text to the focused field
  text-clear  --host <ip>       clear the focused field
  text-get    --host <ip>       print current text of the focused field
  focus-state --host <ip>       watch keyboard focus events (Ctrl-C to stop)
  swipe <up|down|left|right> --host <ip>   touchpad swipe
  tap         --host <ip>       touchpad tap (select)

Options:
  --host <ip>            Apple TV address (required except scan)
  --port <port>          Companion port (default: resolve via mDNS)
  --name <name>          device name shown on the TV while pairing
  --credentials <file>   credentials store (default: credentials.json)
  --dump-frames          hex-dump all frames (crypto payloads truncated)
"""

fun main(args: Array<String>) {
    if (args.isEmpty() || args[0] == "--help" || args[0] == "-h") {
        println(USAGE)
        return
    }
    val command = args[0]
    val positional = mutableListOf<String>()
    val options = mutableMapOf<String, String>()
    var i = 1
    while (i < args.size) {
        val arg = args[i]
        if (arg.startsWith("--")) {
            val key = arg.removePrefix("--")
            if (key == "dump-frames") {
                options[key] = "true"
            } else {
                options[key] = args.getOrNull(i + 1) ?: fail("missing value for --$key")
                i++
            }
        } else {
            positional.add(arg)
        }
        i++
    }

    try {
        runBlocking {
            Commands(options).run(command, positional)
        }
    } catch (e: Exception) {
        System.err.println("error: ${e.message}")
        exitProcess(1)
    }
}

fun fail(message: String): Nothing {
    System.err.println("error: $message")
    System.err.println(USAGE)
    exitProcess(2)
}
