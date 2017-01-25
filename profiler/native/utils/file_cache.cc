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
#include "file_cache.h"

#include <unistd.h>
#include <mutex>

#include "utils/current_process.h"
#include "utils/fs/disk_file_system.h"
#include "utils/stopwatch.h"
#include "utils/thread_name.h"

namespace {
using profiler::Clock;

const int32_t kCacheLifetimeS = Clock::h_to_s(1);
const int32_t kCleanupPeriodS = Clock::m_to_s(1);

// Run thread much faster than cache cleanup periods, so we can interrupt on
// short notice.
const int32_t kSleepUs = Clock::ms_to_us(200);
}

namespace profiler {

using std::lock_guard;
using std::mutex;
using std::shared_ptr;
using std::string;
using std::unique_ptr;
using std::vector;

FileCache::FileCache()
    : FileCache(unique_ptr<FileSystem>(new DiskFileSystem())) {}

FileCache::FileCache(unique_ptr<FileSystem> fs) : fs_(std::move(fs)) {
  // Since we're restarting perfd, nuke any leftover cache from a previous run
  auto cache_root = fs_->NewDir(CurrentProcess::dir() + "cache/");
  cache_partial_ = cache_root->NewDir("partial");
  cache_complete_ = cache_root->NewDir("complete");

  is_janitor_running_ = true;
  janitor_thread_ = std::thread(&FileCache::JanitorThread, this);
}

FileCache::~FileCache() {
  is_janitor_running_ = false;
  janitor_thread_.join();
}

void FileCache::AddChunk(const string &cache_id, const string &chunk) {
  auto file = cache_partial_->GetOrNewFile(cache_id);
  file->OpenForWrite();
  file->Append(chunk);
  file->Close();
}

void FileCache::Abort(const std::string &cache_id) {
  cache_partial_->GetFile(cache_id)->Delete();
}

shared_ptr<File> FileCache::Complete(const std::string &cache_id) {
  auto file_from = cache_partial_->GetFile(cache_id);
  auto file_to = cache_complete_->GetFile(cache_id);
  file_from->MoveContentsTo(file_to);

  return file_to;
}

shared_ptr<File> FileCache::GetFile(const std::string &cache_id) {
  return cache_complete_->GetFile(cache_id);
}

void FileCache::JanitorThread() {
  SetThreadName("FileCache");

  Stopwatch stopwatch;
  while (is_janitor_running_) {
    if (Clock::ns_to_s(stopwatch.GetElapsed()) >= kCleanupPeriodS) {
      cache_complete_->Walk([this](const PathStat &pstat) {
        if (pstat.type() == PathStat::Type::FILE &&
            pstat.modification_age() > kCacheLifetimeS) {
          cache_complete_->GetFile(pstat.rel_path())->Delete();
        }
      });
      stopwatch.Start();
    }

    usleep(kSleepUs);
  }
}

}  // namespace profiler
