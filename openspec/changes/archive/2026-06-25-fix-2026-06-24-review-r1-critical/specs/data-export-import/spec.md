## ADDED Requirements

### Requirement: Zip Slip prevention on import

`core/data/export/ZipHelper.readZip(file: File, targetDir: File)` MUST defend against Zip Slip path traversal. For each zip entry, the resolver MUST compute the destination `File` as `File(targetDir, entry.name)`, then call `.canonicalPath` and verify the result either equals `targetDir.canonicalPath` or starts with `"${targetDir.canonicalPath}/"`. If the resolved path escapes the target directory, the function MUST throw `ImportRejected(reason = "Zip entry escapes target: $entryName")` and MUST NOT extract any further entries.

#### Scenario: Legitimate entry extracts
- **WHEN** the zip contains `notes.json` and `attachments/abc.png`
- **THEN** both extract into `targetDir/notes.json` and `targetDir/attachments/abc.png`

#### Scenario: Traversal entry rejected
- **WHEN** the zip contains an entry named `../../../etc/passwd` or `..\\..\\evil.exe`
- **THEN** `readZip` throws `ImportRejected` and zero bytes are written outside `targetDir`

#### Scenario: Absolute path entry rejected
- **WHEN** the zip contains an entry named `/etc/passwd`
- **THEN** `readZip` throws `ImportRejected` (absolute paths never extract)

#### Scenario: Symbolic link style entry rejected
- **WHEN** the zip contains an entry whose name resolves through `..` segments to escape `targetDir`
- **THEN** `readZip` throws `ImportRejected` regardless of platform path separator