load("//tools/base/bazel:jasmin.bzl", "jasmin_library")
load(":instrumenter.bzl", "compile_and_instrument_base")
load(":instrumenter.bzl", "compile_and_instrument_patch")
load("//tools/base/bazel:bazel.bzl", "fileset")

fileset(
    name = "sources",
    srcs = glob(["src/main/java/**/*.java"]),
    visibility = ["//tools/base/build-system/gradle-core:__pkg__"],
)

java_library(
    name = "instant-run-instrumentation",
    srcs = [":sources"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-debug-all",
        "//tools/base/third_party:org.ow2.asm_asm-tree",
    ],
)

java_library(
    name = "instant-run-test-instrumenter",
    srcs = glob([
        "src/instrumenter/java/**/*.java",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        ":instant-run-instrumentation",
        "//tools/base/annotations",
        "//tools/base/bazel:utils",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-debug-all",
        "//tools/base/third_party:org.ow2.asm_asm-tree",
    ],
)

java_binary(
    name = "incremental-support-visitor",
    main_class = "com.android.build.gradle.internal.incremental.TestIncrementalSupportVisitor",
    visibility = ["//visibility:private"],
    runtime_deps = [":instant-run-test-instrumenter"],
)

java_binary(
    name = "incremental-change-visitor",
    main_class = "com.android.build.gradle.internal.incremental.TestIncrementalChangeVisitor",
    visibility = ["//visibility:private"],
    runtime_deps = [":instant-run-test-instrumenter"],
)

compile_and_instrument_base("incremental-support-visitor")

compile_and_instrument_patch("changeBaseClass", "incremental-change-visitor")

compile_and_instrument_patch("changeSubClass", "incremental-change-visitor")

compile_and_instrument_patch("lineChangingVerifier", "incremental-change-visitor")

compile_and_instrument_patch("verifier", "incremental-change-visitor")

java_test(
    name = "instant-run-instrumentation-tests",
    srcs = glob(["src/test/java/**/*.java"]),
    data = [
        ":base-test-classes",
        ":instrument-base-classes",
        ":instrument-changeBaseClass-classes",
        ":instrument-changeSubClass-classes",
        ":instrument-lineChangingVerifier-classes",
        ":instrument-verifier-classes",
        ":lineChangingVerifier-test-classes",
        ":verifier-test-classes",
    ],
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(
        include = ["manifest-merger/src/test/java/**"],
        exclude = ["manifest-merger/src/test/java/**/*.java"],
    ),
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = ["//tools/base/testutils:tools.testutils"],
    deps = [
        ":instant-run-instrumentation",
        ":instrument-base-classes",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/instant-run:instant-run-common",
        "//tools/base/instant-run/instant-run-runtime",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.mockito_mockito-all",
        "//tools/base/third_party:org.ow2.asm_asm-debug-all",
    ],
)