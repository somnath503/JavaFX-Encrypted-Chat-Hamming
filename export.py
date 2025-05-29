import os

# Get the directory where the script is located (assuming it's the project root)
# This is crucial for calculating relative paths correctly for pom.xml and src files.
script_dir = os.path.dirname(os.path.abspath(__file__))

# Extensions to skip (case-insensitive comparison is recommended)
# Added more common build artifacts, binary files, and IDE files.
skip_exts = [
    '.class', '.jar', '.war', '.ear', '.zip', '.tar', '.gz', '.rar', # Compiled/Archives
    '.log', '.lock', '.bak', '.tmp', # Logs, Locks, Temporary, Backups
    '.tsbuildinfo', '.js.map', '.css.map', # Build artifacts, Source Maps
    '.woff', '.woff2', '.ttf', '.eot', '.svg', '.png', '.jpg', '.jpeg', '.gif', '.ico', # Binary Assets (images, fonts etc.)
    '.iml', '.ipr', '.iws', # IntelliJ IDEA project files
    '.swp', '.swo', # Vim swap files
    '.DS_Store', # macOS system file
    # Add other extensions to skip if needed, e.g., specific config file types
    # Example: '.json', '.yml', '.yaml', '.properties', # Config files - uncomment if you don't need these
    # NOTE: We specifically *don't* skip .java or .fxml or .xml (unless they match a config extension if added above)
]

# Directories to skip (matched by name during os.walk traversal)
# Added common version control, build tool, and IDE directories.
skip_dirs = [
    'node_modules', 'dist', 'build', 'out', # Common build output directories
    'target', # Maven build output
    'logs',
    '.idea', '.git', '.svn', '.hg', # Version control and IDE directories
    '.mvn', '.gradle', # Build tool specific directories
    '__pycache__', # Python cache
    'vendor' # PHP Composer dependencies - adjust if not applicable
    # Add other directory names you want to exclude here
]

# Define the output file path (in the project root directory)
output_file_name = "banking.txt" # User-specified name
output_file_path = os.path.join(script_dir, output_file_name)

print(f"🚀 Starting source code collection...")
print(f"Project root assumed to be: {script_dir}")
print(f"Output will be saved to: {output_file_path}")

# Open the output file once for writing all contents
# Use 'w' mode to overwrite if it exists, use utf-8 encoding
try:
    with open(output_file_path, 'w', encoding='utf-8') as output_file:

        # --- 1. Handle pom.xml in the project root ---
        pom_path = os.path.join(script_dir, "pom.xml")
        if os.path.exists(pom_path) and os.path.isfile(pom_path):
            print("Including pom.xml...")
            try:
                with open(pom_path, 'r', encoding='utf-8') as source_file:
                    content = source_file.read()
                    # Use just the filename as the header as it's in the root
                    output_file.write(f"--- pom.xml ---\n")
                    output_file.write(content)
                    output_file.write("\n") # Add a newline after the file content
            except Exception as e:
                print(f"Error reading pom.xml: {e}")
                output_file.write(f"\n--- ERROR READING pom.xml ---\n")
                output_file.write(f"Error: {e}\n")
        else:
            print("pom.xml not found in the project root, skipping.")


        # --- 2. Walk through the src directory ---
        # We explicitly walk 'src' to ensure we only get files from there
        src_root = os.path.join(script_dir, "src")
        if os.path.exists(src_root) and os.path.isdir(src_root):
            print(f"Walking directory: {src_root}")

            # os.walk yields tuples: (current_dir_path, list_of_subdirs_in_current, list_of_files_in_current)
            for dirpath, dirnames, filenames in os.walk(src_root, followlinks=False): # followlinks=False avoids symlink loops

                # --- Modify dirnames in-place to skip unwanted directories ---
                # This is crucial for os.walk to not descend into directories listed in skip_dirs
                # We iterate over a copy of dirnames ([:]) to safely modify the list while iterating
                dirnames[:] = [d for d in dirnames if d not in skip_dirs]

                # print(f"Visiting: {dirpath}, Will visit next: {dirnames}") # Debugging walk

                for f in filenames:
                    filepath = os.path.join(dirpath, f)

                    # Check if the file extension is in skip_exts (case-insensitive)
                    # os.path.splitext returns a tuple: ('base', '.ext')
                    file_extension = os.path.splitext(f)[1].lower()
                    if file_extension in skip_exts:
                         # print(f"Skipping '{filepath}' due to extension '{file_extension}'") # Debug
                         continue # Skip this file

                    # Check if the file itself is named like a directory we should skip (rare but possible)
                    if f in skip_dirs: # Check filename against skip_dirs names
                         # print(f"Skipping file '{filepath}' named like a skip_dir") # Debug
                         continue

                    # --- If we reached here, the file should be included ---

                    # Construct the relative path from the project root (script_dir)
                    # This handles files directly in src/ and files in subdirectories of src/
                    relpath = os.path.relpath(filepath, script_dir)

                    print(f"Including: {relpath}") # Indicate which file is being included

                    try:
                        # Read file content
                        with open(filepath, 'r', encoding='utf-8') as source_file:
                            content = source_file.read()

                            # Write header and content
                            # Add a newline before the header to separate files clearly
                            output_file.write(f"\n--- {relpath} ---\n")
                            output_file.write(content)
                            output_file.write("\n") # Ensure a newline after the file content

                    except Exception as e:
                        # Handle files that cannot be read (e.g., permission errors, unexpected binary files)
                        print(f"Error reading file '{relpath}': {e}")
                        # Add an error marker in the output file as well
                        output_file.write(f"\n--- ERROR READING {relpath} ---\n")
                        output_file.write(f"Error: {e}\n")
                        output_file.write("\n")

            print("Finished walking src directory.")

        else:
            print(f"Directory '{src_root}' not found or is not a directory, skipping source code walk.")

    print(f"\n✅ Source code collection complete. Contents saved into '{output_file_name}'.")

except Exception as e:
    # Handle errors that occur before or during the file opening/writing process
    print(f"\n❌ An error occurred while writing to the output file {output_file_path}: {e}")