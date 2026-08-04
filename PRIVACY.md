# Privacy Policy

Quits is a free and open-source expense-splitting app, meant to protect your privacy.
Here's what happens to your data.

## The short version

- Quits works fully offline. Your groups, expenses, and balances live on your
  device.
- If you turn on sync, your data is **end-to-end encrypted** before it ever
  leaves your phone. A relay server only ever sees scrambled data it can't
  read.
- There are no accounts. Quits never asks for your name, email, or phone number.
- No ads. No analytics. No trackers. No selling data. None of that.

## What stays on your device

Everything you type into Quits — group names, members, expenses, splits,
settlements — is stored in a local database on your phone. If you never turn on
sync, none of it goes anywhere.

## What happens when you turn on sync

Sync lets you share a group across devices and with the people you're splitting
with. When it's on:

- Your group data is **encrypted on your device** with a key derived from the
  group's secret. That secret is shared through the invite link/code and never
  reaches the server.
- The relay server stores only the **encrypted blob**, an anonymous group
  identifier the app derives locally, a device id (to order changes correctly),
  and timestamps. It cannot read your expenses, names, or amounts — it just
  passes the encrypted data between devices.
- Anyone with the invite link/code can join the group and read its contents,
  because they get the key. Only share invite links with people you trust.

You can use the free public relay (`quits.eloque.nz`) or **host your own** — the
server is open source. If you self-host, that data lives entirely on your
server.

## Currency conversion

If you use multiple currencies, the app fetches exchange rates from
[Frankfurter](https://frankfurter.dev), a free public rate service. Only the
currency codes (like `EUR` → `USD`) are sent — never your amounts or any
personal data.
