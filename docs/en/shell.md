# Remote Shell (Conch)

橘子岛 can execute commands on remote machines through the [Conch](https://github.com/orangeisland/conch) protocol — an end-to-end encrypted secure shell designed for AI agents.

## How It Works

```text
橘子岛 (Android)  ──ECDH + AES-256-GCM──▶  Conch Server (Linux/macOS/Windows)
                                           │
                                           ├── Execute commands
                                           ├── Read/write/edit files
                                           ├── Glob and grep search
                                           └── Return results
```

The model decides when to use the shell — it can check server status, manage files, run scripts, or troubleshoot issues autonomously.

## Security

Conch uses strong encryption and anti-abuse protections:

- **ECDH key exchange** — ephemeral keys per session
- **AES-256-GCM encryption** — all traffic encrypted
- **HMAC-SHA256 signing** — message integrity verified
- **Token bucket rate limiting** — prevents abuse
- **Nonce-based anti-replay** — each request is unique

!!! note
    Commands execute with the permissions of the user running the Conch server. Use a restricted user account for sensitive environments.

---

## Setup

### Step 1: Deploy Conch Server

Deploy the Conch server on your target machine. See the [Conch repository](https://github.com/orangeisland/conch) for setup instructions.

### Step 2: Add Device in 橘子岛

1. Go to **Settings → Shell**
2. Enable **Shell Tool**
3. Tap **Add Device**
4. Choose the device type: **Conch** or **SSH**
5. Fill in the device details:

=== "Conch"

    | Field | Description | Example |
    |-------|-------------|---------|
    | **Name** | Display name for this device | `Build Server` |
    | **Description** | Optional note about this machine | `Office Ubuntu box` |
    | **Server URL** | Conch server endpoint (host:port) | `http://192.168.1.100:14216` |
    | **API Key** | Authentication token | From Conch server config |
    | **Timeout** | Command timeout in seconds | `30` |

=== "SSH"

    | Field | Description | Example |
    |-------|-------------|---------|
    | **Name** | Display name for this device | `VPS Server` |
    | **Description** | Optional note about this machine | `Production web server` |
    | **Host** | SSH hostname or IP address | `192.168.1.200` |
    | **Port** | SSH port | `22` |
    | **User** | SSH username | `root` |
    | **Password** | SSH password | Your SSH password |

Tap **Add** to save.

### Step 3: Use

Once configured, the model can access the device. There's no manual trigger — the model auto-discovers available shell devices and calls them when appropriate.

---

## Multi-Device Support

Add multiple shell devices to let the model work across machines:

- **Build server** — compile and test code
- **Home lab** — manage self-hosted services
- **Development VM** — edit code and run scripts

Each device is independently configured with its own name, URL, and credentials. The model can distinguish between them and choose the right device for each task.

---

## Available Operations

### Command Execution (`shell_execute`)

Run any shell command and receive stdout, stderr, and exit code.

### File Operations

| Tool | Function |
|------|----------|
| `file_read` | Read a file from the remote filesystem |
| `file_write` | Write or overwrite a file |
| `file_edit` | Perform exact string replacements in a file |
| `file_glob` | Find files matching a glob pattern |
| `file_grep` | Search file contents with regex |

All file operations go through the encrypted Conch channel.

---

## MCP Integration

Conch can also serve as a **Claude Desktop MCP server**. If you use Claude Code or another MCP client, you can configure Conch as a tool provider for remote file and shell access from your desktop.

See the [Conch documentation](https://github.com/orangeisland/conch) for MCP setup instructions.

---

## Troubleshooting

### Device shows as unavailable
- Check the Conch server is running
- Verify the URL is reachable from your Android device
- Check firewall rules on the server

### Commands time out
- Increase the timeout in device settings
- Check that the command isn't hanging (require user input, etc.)

### Authentication fails
- Verify the API key matches the server config
- Regenerate keys if needed
