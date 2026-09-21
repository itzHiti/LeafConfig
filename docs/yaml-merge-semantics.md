# YAML merge semantics

LeafConfig never round-trips a file through Java objects. Three
representations exist:

1. **Schema** – immutable metadata discovered from the class.
2. **Document** – the YAML syntax tree with comments, order and unknown nodes.
3. **Instance** – the typed Java object handed to the plugin.

## Load pipeline

1. Resolve the file name inside the base directory (absolute names, traversal
   and symlinks that leave the directory are `UNSAFE_PATH`).
2. Instantiate the class to obtain defaults.
3. Missing file: build the document from defaults and write it atomically.
   Existing file: check size, decode strictly as UTF-8, parse with comments,
   duplicate keys rejected, anchors/aliases and non-core tags rejected, limits
   applied.
4. Decode every known key, collecting every error.
5. Apply `@Required`, `@NotBlank`, `@Range`, `@Pattern`, then programmatic
   validators.
6. Any error: fail, file and current snapshot untouched.
7. Merge missing keys and schema comments into the document.
8. Write only if the merge changed something.
9. Publish the instance.

## Merge rules

Proven by the golden fixtures in
`leafconfig-yaml/src/test/resources/golden` (each case is `input.yml` before
and `expected.yml` after one load):

| Rule | Fixture |
|---|---|
| Unknown keys are never removed | `preserve-everything`, `nested-partial` |
| Existing keys are never reordered or restyled; quoted and block scalars stay | `preserve-everything` |
| A new key goes directly after its nearest preceding schema neighbour present in the file; without one, directly before the nearest following neighbour; otherwise at the end | `add-defaults`, `empty-collections-and-null` |
| A first key that carries comments keeps its position so header comments stay on top | `unicode-crlf` |
| Schema comments are added only to keys without a leading comment | `schema-comment-insertion` |
| Nested sections receive missing keys recursively; `{}` becomes a block mapping | `nested-partial`, `empty-collections-and-null` |
| Indent width and indented/unindented sequences follow the file | `four-space-indent`, `unindented-sequences` |
| CRLF and a UTF-8 BOM are preserved | `unicode-crlf` |
| First generation writes the type-level header, a blank line, then keys | `first-generation` |
| Malformed YAML and duplicate keys leave the file byte for byte untouched | `GoldenFileTest` |
| Unchanged documents are not rewritten | `ConfigManagerTest.unchangedDocumentIsNotRewritten` |

## Known limitations

- Folded scalars (`>`) are re-folded; the value is unchanged but line breaks
  inside the scalar are not preserved.
- A file with only comments (no document) loses those comments when defaults
  are written into it.
- Merge does not descend into `Map<String, Nested>` values or sequence
  elements; only mapping sections that correspond to nested configuration
  objects receive new keys.
- Generated files use two-space indentation, indented sequences and LF.

## Writing

Writes go to a sibling temporary file, are forced to disk, and are moved over
the target with `ATOMIC_MOVE`. Where the file system does not support atomic
moves, a plain `REPLACE_EXISTING` move is used. The temporary file is removed on
failure and the target is never truncated first.
