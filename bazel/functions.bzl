# Helper functions for implementing Bazel rules.

def create_option_file(ctx, path, content):
  """ Create the command line options file """
  options_file = ctx.new_file(path)
  ctx.file_action(output=options_file, content=content)
  return options_file

def create_java_compiler_args_srcs(ctx, srcs, path, deps):
  args = []
  option_files = []

  # Classpath
  if deps:
    cp_file = create_option_file(ctx, path + ".cp",
      ":".join([dep.path for dep in deps]))
    option_files += [cp_file]
    args += ["-cp", "@" + cp_file.path]

  # Output
  args += ["-o", path]

  # Source files
  source_file = create_option_file(ctx, path + ".lst", srcs)
  option_files += [source_file]
  args += ["@" + source_file.path]

  return (args, option_files)


def create_java_compiler_args(ctx, path, deps):
  return create_java_compiler_args_srcs(
      ctx,
      "\n".join([src.path for src in ctx.files.srcs]),
      path,
      deps)


# Adds an explict target-name part if label doesn't have it.
def explicit_target(label):
  return label if ":" in label else label + ":" + label.rsplit("/", 1)[-1]
