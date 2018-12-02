# secretary

Make create and manage own [launchd.plist](https://www.manpagez.com/man/5/launchd.plist/) a little easier

## Usage

```bash
# secretary --help

secretary <command>

Commands:
  secretary list               List all defined services
  secretary enable [service]   Enable service
  secretary disable [service]  Disable service
  secretary reload [service]   Regenerate plist file and restart service
  secretary start [service]    Delegate to `launchctl start [service label]`
  secretary stop [service]     Delegate to `launchctl stop [service label]`
  secretary plist [service]    Print service plist file path
  secretary edit [service]     Create or edit service definition file by EDITOR

Options:
  --version    Show version number                                     [boolean]
  --debug, -d  Print error stack                                       [boolean]
  --help       Show help                                               [boolean]
```
