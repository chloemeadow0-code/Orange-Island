# Sandbox

橘子岛 can run a lightweight Alpine Linux environment locally on your device — no internet connection required. The sandbox lets the model install packages and execute commands in an isolated root filesystem.

!!! note "Availability"
    The sandbox is available in all builds. Access it from **Settings → Shell → Sandbox Management** or directly via **Settings → Sandbox**.

## How It Works

The sandbox uses an Alpine Linux root filesystem deployed to your device's app-private storage. A minimal `apk`-based package manager lets you install software into this environment, and commands execute inside a proot-based container. The sandbox also integrates with Android's **Storage Access Framework (SAF)** via a DocumentsProvider, allowing other apps to access sandbox files.

This is **not** a full virtual machine — it's a lightweight user-space container that shares the host kernel. It provides enough isolation for safe experimentation while keeping resource usage low.

---

## VPN Interference — Critical

!!! danger "Turn Off Your VPN Before Using Sandbox Networking"

    VPN applications interfere with proot's DNS resolution. Here's why:

    **Root cause — PRoot has no network namespace isolation.**

    PRoot uses `ptrace` to intercept syscalls and redirect file paths, but it does **not** support `CLONE_NEWNET` (Linux network namespaces). All processes inside the sandbox share the host Android system's network stack directly. There is no virtual network interface, no isolated routing table, and no independent DNS configuration.

    **How a VPN on Android breaks DNS inside proot:**

    1. Android VPN apps use the `VpnService` API, which creates a **TUN interface** — a virtual network device that intercepts **all** device traffic, including traffic from inside proot
    2. To prevent DNS leaks outside the encrypted tunnel, the VPN **redirects all port 53 (DNS) traffic** to its own DNS servers
    3. Inside proot, when an application calls `getaddrinfo()` (the standard libc DNS resolver), the request goes through Android's system resolver — which the VPN has already intercepted
    4. On Android 12+, Google reworked the DNS resolver, making `getaddrinfo()` inside proot environments particularly fragile ([termux/proot#215](https://github.com/termux/proot/issues/215))
    5. The VPN's TUN routing and the system resolver's DNS path conflict inside proot: the resolver sends a DNS query, the VPN TUN intercepts it, but the response never reaches back through proot's `ptrace` layer

    **Observed symptoms:**

    | Operation | Result |
    |-----------|--------|
    | `ping 1.1.1.1` | ✅ Works (direct IP, no DNS needed) |
    | `ping google.com` | ❌ Fails — "Temporary failure in name resolution" |
    | `apk add python3` | ❌ Fails — cannot resolve `dl-cdn.alpinelinux.org` |
    | `curl https://example.com` | ❌ Fails — name resolution error |
    | `curl https://1.1.1.1` | ✅ Works (IP direct connection) |

    **Fix:** Turn off your VPN entirely before performing any network operation in the sandbox (installing packages, `curl`, `wget`, etc.). You can re-enable the VPN after network operations complete.

    This is a fundamental limitation of proot's architecture — it cannot virtualize the network stack when an Android VPN overrides the system's DNS routing via a TUN interface.

---

## Setup

### Install Root Filesystem

The first time you open the sandbox, you'll see a dashboard indicating the rootfs is not installed. Tap **Install** to download and extract the Alpine root filesystem.

!!! info "Storage Usage"
    The base rootfs uses approximately 100–200 MB. Installed packages consume additional space. Total disk usage is shown on the dashboard.

---

## Package Management

### Install a Package

1. Type the package name in the text field (e.g., `python3`)
2. Tap **Install**
3. Watch the terminal output for installation progress

Alternatively, tap any **quick install chip** for common packages:

```
python3   git      curl      wget
openssh   nodejs   build-base   htop
```

### Installed Packages

Below the install section, all installed packages are listed with their:

- **Name** — the Alpine package name
- **Version** — the installed version
- **Description** — a brief summary (truncated)

### Remove a Package

Tap the :material-close: icon on any installed package to remove it. A confirmation dialog appears before deletion.

---

## Dashboard

When the sandbox is ready, the dashboard shows:

- **Disk usage** — a progress bar and numeric display (MB or GB)
- **Installed count** — total number of packages

---

## Terminal Output

When installing or removing packages, terminal output appears in a dark-themed, scrollable monospace view below the input field. The output auto-scrolls to follow the latest lines.

Use this to:
- Monitor installation progress
- Debug failed package operations
- See what files a package installs

---

## Reset Sandbox

The **Danger Zone** at the bottom contains a **Reset Sandbox** option. This completely removes the root filesystem and all installed packages.

!!! danger "Destructive Action"
    Resetting the sandbox deletes the entire Alpine environment. You will need to reinstall the rootfs and all packages afterward. A confirmation dialog prevents accidental resets.
