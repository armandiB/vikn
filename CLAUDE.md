# vikn — SuperCollider class-library extension (Quark)

This is a class library loaded by SuperCollider at startup from the Extensions
directory. It is consumed by the HomewareSC project.

## Hard rules
- `.sc` files here are compiled once, at interpreter startup. An edit is not
  live until the class library is recompiled.
- One class per concept, class name must match the file name convention already
  used here. A duplicate class name anywhere breaks the entire library.
- Changing a method signature can break HomewareSC. Grep HomewareSC for callers
  before changing any public method.
- Document public classes in `HelpSource/Classes/<ClassName>.schelp` using
  schelp markup, matching the style of SuperCollider's own help files.
- Stage files individually by path. Never `git add -A` or `git add .`.

## Verification
After any edit, the class library must still compile:
`~/Music/Supercollider/HomewareSC/scripts/recompile-check.sh`