# Privacy Policy

**Effective date:** 1 September 2026  
**Last updated:** 1 September 2026

This Privacy Policy describes how **YaPcore** (software maintained by **YapLabs** and
contributors) relates to personal information. Read it together with
[Terms of Use](TERMS_OF_USE.md) and [Licensing](LICENSING.md).

> **Not legal advice.** This document explains how the YaPcore *software* works with
> data. If you operate a **public Minecraft server** or a commercial service, you are
> responsible for your own privacy compliance. Consult qualified counsel for your
> jurisdiction.

---

## 1. Who this policy applies to

| Role | What this policy covers |
|------|-------------------------|
| **You download or build YaPcore** | How the software may process data on **your** machine |
| **You run a server for others** | You are the **data controller** for your players — publish **your own** policy |
| **You play on someone else's YaPcore server** | That **server operator's** policy applies, not YapLabs |

**YapLabs** provides open-source server software. We do **not** operate player accounts,
host gameplay worlds for the public, or receive player data from your installation unless
you separately contact us (e.g. via GitHub).

YaPcore is **not affiliated with Mojang Studios or Microsoft**. Minecraft is subject to
[Mojang's policies](https://www.minecraft.net/en-us/terms) and the
[Minecraft EULA](https://www.minecraft.net/en-us/eula).

---

## 2. Information the software may store locally

When you install and run YaPcore on hardware you control, the stack may read, write, or
display data including:

### 2.1 Game and player data

| Data | Typical location | Purpose |
|------|------------------|---------|
| Minecraft username, UUID | World files, `plugins/YaPPlayerdata/`, MariaDB | Gameplay, sync, ranks |
| IP address | Server logs, moderation records, session lock | Security, bans, audit |
| Chat messages | Logs, `YaPChat`, moderation plugin | Chat, filters, staff tools |
| Inventory, location, stats | Playerdata / DB plugins | Progression, skills, kits |
| Punishment history | `YaPModeration`, guard plugins | Bans, mutes, warnings |

Retention is **controlled by you** (log rotation, DB backups, plugin config).

### 2.2 Operator and configuration data

| Data | Typical location | Purpose |
|------|------------------|---------|
| Dashboard access token | `config/server.properties` | Web admin authentication |
| Operator names (`ops=`) | `config/server.properties` | In-game admin |
| Rank / permission nodes | `plugins/YaPPerms/` | Access control |
| Server MOTD, rules | `plugins/YaPEssentials/` | Player-facing text |

Protect `web-dashboard-token` and database credentials. Do not expose the web dashboard
(`:8080`) to the internet without TLS and strong access controls — see
[WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) and [EDGE_HARDEN.md](../network/EDGE_HARDEN.md).

### 2.3 Diagnostics

| Data | Typical location | Purpose |
|------|------------------|---------|
| Console output | `logs/`, web dashboard console | Debugging |
| Crash dumps | `logs/crashes/` | Support (`crashdump` command) |
| Metrics (optional) | `/metrics` endpoint | Monitoring |

---

## 3. Optional integrations (operator-enabled)

YaPcore can connect to services **you** configure. Each has its own privacy terms:

| Integration | Examples of data sent | Operator action |
|-------------|----------------------|-----------------|
| **MariaDB / YaPDB** | Player sync rows, economy | You choose host & DPA |
| **Tebex** (optional) | Purchase events, UUID | [TEBEX.md](../ops/TEBEX.md) |
| **Discord** (`YaPDiscord`) | Chat relay, webhooks | Your bot token & Discord ToS |
| **Grim AC** (optional) | Movement / combat telemetry | [GRIM.md](../ops/GRIM.md) |
| **Public HTTP** | Resource pack downloads | Access logs on your edge |

YapLabs does not receive this data unless the third party or you share it with us.

---

## 4. Source repository and releases

If you use **GitHub**, **release zips**, or **issue trackers** to obtain YaPcore:

- GitHub may collect usage data under [GitHub's Privacy Statement](https://docs.github.com/en/site-policy/privacy-policies/github-privacy-statement).
- We do not require an account to download GPLv3 source or release artifacts.
- Contributions (issues, PRs) are public unless you use private channels you arrange separately.

---

## 5. Children's privacy

Minecraft is played by people of many ages. **Server operators** must comply with laws
such as COPPA (US), GDPR (EU/UK), and similar rules when collecting personal information
from children.

YaPcore software does not include age verification. If you run a server open to minors:

- Publish a clear privacy policy on your server website or `/rules`.
- Obtain parental consent where required.
- Minimize collection (avoid storing IP longer than needed, restrict public chat logs).

---

## 6. Security recommendations for operators

1. Keep `web-dashboard-localhost-only=true` unless you need remote admin access.
2. Rotate `web-dashboard-token` after staff changes.
3. Use strong MariaDB passwords and firewall database ports.
4. Restrict who has OP, `yapadmin.menu`, and dashboard access.
5. Review [EDGE_HARDEN.md](../network/EDGE_HARDEN.md) before exposing ports publicly.

---

## 7. Your rights (players)

If you play on a **third-party** YaPcore server, contact that **server owner** to:

- Access or delete data they hold about you  
- Opt out of optional features (Discord linking, analytics, etc.)

YapLabs cannot fulfill player data requests for servers we do not operate.

---

## 8. Changes to this policy

We may update this document when YaPcore features change. The **Last updated** date at the
top will change. Material changes will be noted in the project changelog or release notes.

Continued use of YaPcore after an update constitutes acceptance of the revised policy for
**software download and documentation** purposes. Server operators remain responsible for
notifying **their** players separately.

---

## 9. Contact

- **Project / documentation:** open a GitHub issue in the YaPcore repository  
- **Licensing:** [LICENSING.md](LICENSING.md)  
- **Terms:** [Terms of Use](TERMS_OF_USE.md)

---

## 10. Template for public server operators

You may adapt the following for your server website (replace bracketed text):

> **Privacy Policy — [Your Server Name]**  
> We operate a Minecraft server using YaPcore software. We collect [usernames, UUIDs, IP
> addresses, chat logs, …] to provide gameplay, prevent abuse, and enforce rules. Data is
> stored on [our VPS / dedicated host] for [retention period]. We use [Tebex / Discord /
> none] for [payments / community]. Contact [your email] for data requests or deletion.
> We do not sell personal information. Minors: [your policy].

This template is **not** legal advice.
