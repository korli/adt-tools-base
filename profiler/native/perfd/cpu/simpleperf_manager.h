/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef CPU_SIMPLEPERFMANAGER_H_
#define CPU_SIMPLEPERFMANAGER_H_

#include <map>
#include <mutex>
#include <string>

#include "utils/clock.h"

namespace profiler {

// Entry storing all data related to an ongoing profiling.
struct OnGoingProfiling {
 public:
  int pid;             // App pid being profiled.
  int simpleperf_pid;  // Simpleperf pid doing the profiling.

  std::string trace_path;     // filepath where trace will be made availabe.
  std::string output_prefix;  // filename pattern for trace, tmp trace and log.
  std::string app_dir;        // directory to app base (e.g: /data/data/c.w.o/)
  std::string log_filepath;  // If something happen while simpleperf is running,
                             // store logs in this file.
  std::string app_pkg_name;
};

class SimplePerfManager {
 public:
  explicit SimplePerfManager(const Clock &clock) : clock_(clock) {}
  ~SimplePerfManager();

  // Returns true if profiling of app |app_pkg_name| was started successfully.
  // |trace_path| is also set to where the trace file will be made available
  // once profiling of this app is stopped. To call this method on an already
  // profiled app is a noop.
  bool StartProfiling(const std::string &app_pkg_name, int sampling_interval_us,
                      std::string *trace_path, std::string *error);
  bool StopProfiling(const std::string &app_pkg_name, std::string *error);

 private:
  const Clock &clock_;
  static const char *kSimpleperfExecutable;
  std::map<std::string, OnGoingProfiling> profiled_;
  std::mutex start_stop_mutex_;  // Protects simpleperf start/stop

  // Make sure profiling is enabled on the platform (otherwise LinuxSE prevents
  // it). Returns true on success.
  bool EnableProfiling(std::string *error) const;

  // Generate the filename pattern used for trace and log (a name guaranteed
  // not to collide and without an extension).
  std::string GetFileBaseName(const std::string &app_pkg_name) const;

  // Wait until simpleperf process has returned.
  bool WaitForSimplerPerf(const OnGoingProfiling &ongoing_recording,
                          std::string *error) const;

  // Convert a trace file from simpleperf binary format to protobuffer.
  // Source and Destination are determined by |ongoing_recording| values.
  bool ConvertRawToProto(const std::string &app_pkg_name,
                         const OnGoingProfiling &ongoing_recording,
                         std::string *error) const;

  // Delete log file and temporary file generated by |ConvertRawToProto|.
  void CleanUp(const std::string &app_pkg_name,
               const OnGoingProfiling &ongoing_recording,
               const std::string &tmp_proto_trace) const;

  // Move protobuffer trace from app /data folder to folder specific by
  // |ongoing_recording|.trace_path .
  bool MoveTraceToPickupDir(const OnGoingProfiling &ongoing_recording,
                            const std::string &tmp_proto_trace,
                            const std::string &app_pkg_name,
                            std::string *error) const;

  bool StopSimplePerf(const OnGoingProfiling &ongoing_recording,
                      std::string *error) const;

  // Returns true if the app is currently being profiled by a simple perf
  // process.
  bool IsProfiling(const std::string &app_pkg_name);
};
}

#endif  // CPU_SIMPLEPERFMANAGER_H_
