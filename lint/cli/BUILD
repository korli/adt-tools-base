load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

kotlin_library(
    name = "cli",
    # TODO: move resources out of java?
    srcs = ["src/main/java"],
    pom = ":pom",
    resources = glob(
        include = ["src/main/java/**"],
        exclude = [
            "src/main/java/**/*.java",
            "src/main/java/**/*.kt",
        ],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//prebuilts/tools/common/lint-psi/kotlin-compiler",
        "//prebuilts/tools/common/lint-psi/uast",
        "//tools/base/annotations",
        "//tools/base/build-system:tools.manifest-merger",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/lint:tools.lint-api",
        "//tools/base/lint:tools.lint-checks",
        "//tools/base/lint:tools.lint-kotlin",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-reflect",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
        "//tools/base/third_party:org.jetbrains.trove4j_trove4j",
    ],
)

maven_pom(
    name = "pom",
    artifact = "lint",
    group = "com.android.tools.lint",
    source = "//tools/buildSrc/base:base_version",
)
