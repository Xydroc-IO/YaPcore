# YaPcore release packages

Versioned folders (`1.0.0.0/`, …) are produced locally by:

```bash
gradle publishReleasesFolder
```

Each version directory holds the full linux/windows trees, zip archives, and
standalone suite zips. Contents are gitignored — rebuild when needed.

See [docs/RELEASES.md](../docs/RELEASES.md).
