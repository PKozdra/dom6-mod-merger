# Dominions 6 Mod Merger

A tool for combining multiple Dominions 6 mods into one, with automatic handling of ID collisions.

![image](https://github.com/user-attachments/assets/1ffad9f2-578b-491e-b81e-a1d191c79bee)

## Features

- Merge multiple mods while preserving functionality
- Automatic ID collision resolution
- Support for mod grouping (for example, Sombre Warhammer mods having a co-dependency on original mod file)
- Both GUI and CLI interfaces

## Requirements

- JRE 21 (if using .jar version)

## Installation

### Windows
1. Download the latest `modmerger-win64.zip` from Releases
2. Extract the archive
3. Run the .exe file

### Windows/Linux/MacOS (portable JAR)
1. Download the latest `modmerger-jar.zip` from Releases
2. Extract the archive
3. Launch using the appropriate method for your OS:
   - Windows: Run `/bin/modmerger.bat`
   - Linux/MacOS: Run `./bin/modmerger`

## Usage

### GUI Mode

The default interface provides a graphical way to merge mods:

1. Launch the application
2. Select mods you want to merge from the list
3. Click the merge button
4. Wait for the process to complete
5. Find your merged mod in the output directory

### CLI Mode

The application supports two CLI modes: interactive console and automatic merge.

#### Auto-merge Mode

Use this mode for automated merging through scripts or automation:

```bash
# Windows
./bin/modmerger.bat --auto-merge [options]

# Linux/MacOS
./bin/modmerger --auto-merge [options]
```

Options:
- `--mods <paths>` - List of complete paths to mod files (required)
  - Format: `["/path/to/mod1.dm","/path/to/mod2.dm"]`
- `--output <name>` - Output filename for merged mod (optional)
  - Default: `merged_mod.dm`
- `--output-path <dir>` - Directory to store the merged mod (optional)
  - Default: Current directory
- `--clean` - Clean output directory before merging (optional)
  - Removes all files in the target mod directory

Example:
```bash
modmerger --auto-merge \
    --mods "[/path/to/mod1.dm,/path/to/mod2.dm]" \
    --output merged.dm \
    --output-path /path/to/output \
    --clean
```

Exit Codes:
- 0: Success
- 1: Error (check logs for details)

### Interactive Console Mode

Launch the application with `--console` flag for an interactive CLI experience. This mode allows for step-by-step mod selection and merging.

## Current Limitations

- Limited vanilla entity comparison capabilities
- Work in progress on UI improvements
- Dom5/Dom4 mod conversion to Dom6 still in development

## Support

If you encounter any issues or have suggestions for improvements:
1. Check the existing issues on GitHub
2. Create a new issue with detailed information about your problem
3. Include log files and steps to reproduce the issue

## Acknowledgments

- [Illwinter Game Design](https://www.illwinter.com/) for Dominions 6
- [djmcgill / David McGillicuddy](https://github.com/djmcgill) for original version of Domingler, from which regexes for mod parsing are used in this application
- [larzm42](https://github.com/larzm42/dom6inspector) for raw Dominions 6 csv data dumps
