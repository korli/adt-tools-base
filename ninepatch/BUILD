load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.ninepatch",
    srcs = ["src/main/java"],
    iml_files = ["ninepatch.iml"],
    tags = ["managed"],
    test_srcs = [
        "src/test/java",
        "src/test/resources",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)
