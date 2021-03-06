load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/profiler:perf_test.bzl", "perf_test")

sh_binary(
    name = "art-runner",
    srcs = ["//prebuilts/tools/linux-x86_64/art"],
    data = select({
        "//tools/base/bazel:darwin": [],
        "//tools/base/bazel:windows": [],
        "//conditions:default": [
            ":profiler-service",
            "//prebuilts/tools/linux-x86_64/art:art_deps",
            "//prebuilts/tools/linux-x86_64/art:dex2oat",
            "//tools/base/profiler/app:perfa",
            "//tools/base/profiler/app:perfa_java",
            "//tools/base/profiler/app:perfa_okhttp",
            "//tools/base/profiler/native/perfa:libperfa.so",
            "//tools/base/profiler/tests/android-mock:android-mock-dex",
            "//tools/base/profiler/tests/app-launcher:app-launcher-dex",
        ],
    }),
)

# TODO: Have transform pull in dependency jar files, so we do not need to load multiple
# jar dependencies at runtime.
java_binary(
    name = "profilers-transform-main",
    srcs = [
        "testSrcs/com/android/tools/profiler/transform/ProfilerTransformMain.java",
    ],
    main_class = "com.android.tools.profiler.transform.ProfilerTransformMain",
    resources = ["profiler.properties"],
    runtime_deps = ["//tools/base/profiler/transform"],
    deps = [
        "//tools/base/profiler/transform",
        "//tools/base/testutils:studio.testutils",
    ],
)

dex_library(
    name = "profiler-service",
    jars = ["//tools/base/profiler/app:studio-profiler"],
)

java_library(
    name = "tests",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/common:studio.common",
        "//tools/base/profiler:netty-grpc-jar",
        "//tools/base/profiler:studio-profiler-grpc-1.0-jarjar",
        "//tools/base/profiler/transform",
        "//tools/base/testutils:studio.testutils",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/.idea/libraries:truth",
    ],
)

perf_test(
    name = "HttpTest",
    srcs = [
        "testSrcs/com/android/tools/profiler/BasicPerfTest.java",
        "testSrcs/com/android/tools/profiler/network/HttpUrlTest.java",
        "testSrcs/com/android/tools/profiler/network/OkHttpTest.java",
    ],
    native_lib = "//tools/base/profiler/tests/test-app:libnativetest.so",
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = [
        ":tests",
        "//tools/idea/.idea/libraries:truth",
    ],
)

perf_test(
    name = "MemoryTest",
    srcs = [
        "testSrcs/com/android/tools/profiler/memory/MemoryTest.java",
    ],
    native_lib = "//tools/base/profiler/tests/test-app:libnativetest.so",
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = [
        ":tests",
        "//tools/idea/.idea/libraries:truth",
    ],
)

perf_test(
    name = "JniTest",
    srcs = [
        "testSrcs/com/android/tools/profiler/memory/JniTest.java",
    ],
    native_lib = "//tools/base/profiler/tests/test-app:libnativetest.so",
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = [
        ":tests",
        "//tools/idea/.idea/libraries:truth",
    ],
)

perf_test(
    name = "EventTest",
    srcs = [
        "testSrcs/com/android/tools/profiler/EventProfilerTest.java",
    ],
    native_lib = "//tools/base/profiler/tests/test-app:libnativetest.so",
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = [
        ":tests",
        "//tools/idea/.idea/libraries:truth",
    ],
)
