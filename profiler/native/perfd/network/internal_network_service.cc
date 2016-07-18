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
#include "internal_network_service.h"

#include <grpc++/grpc++.h>
#include <unistd.h>

#include "utils/clock.h"
#include "utils/log.h"
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

using grpc::ServerContext;
using grpc::Status;

InternalNetworkServiceImpl::InternalNetworkServiceImpl(
    const std::string &root_path, NetworkCache *network_cache)
    : network_cache_(*network_cache) {
  fs_.reset(new DiskFileSystem());
  // Since we're restarting perfd, nuke any leftover cache from a previous run
  auto cache_root = fs_->NewDir(root_path + "/cache/network");
  cache_partial_ = cache_root->NewDir("partial");
  cache_complete_ = cache_root->NewDir("complete");

  is_janitor_running_ = true;
  janitor_thread_ =
      std::thread(&InternalNetworkServiceImpl::JanitorThread, this);
}

InternalNetworkServiceImpl::~InternalNetworkServiceImpl() {
  is_janitor_running_ = false;
  janitor_thread_.join();
}

Status InternalNetworkServiceImpl::RegisterHttpData(
    ServerContext *context, const proto::HttpDataRequest *httpData,
    proto::EmptyNetworkReply *reply) {
  auto details =
      network_cache_.AddConnection(httpData->conn_id(), httpData->app_id());
  details->request.url = httpData->url();
  return Status::OK;
}

Status InternalNetworkServiceImpl::SendChunk(ServerContext *context,
                                             const proto::ChunkRequest *chunk,
                                             proto::EmptyNetworkReply *reply) {
  std::stringstream filename;
  filename << chunk->conn_id();

  auto file = cache_partial_->GetOrNewFile(filename.str());
  file->OpenForWrite();
  file->Append(chunk->content());
  file->Close();

  return Status::OK;
}

Status InternalNetworkServiceImpl::SendHttpEvent(
    ServerContext *context, const proto::HttpEventRequest *httpEvent,
    proto::EmptyNetworkReply *reply) {
  switch (httpEvent->event()) {
    case proto::HttpEventRequest::DOWNLOAD_STARTED: {
      auto details = network_cache_.GetDetails(httpEvent->conn_id());
      details->downloading_timestamp = httpEvent->timestamp();
    }

    break;

    case proto::HttpEventRequest::DOWNLOAD_COMPLETED: {
      // Since the download is finished, move from partial to complete
      // TODO: Name the dest file based on a hash of the contents. For now, we
      // don't have a hash function, so just keep the name.
      std::stringstream filename;
      filename << httpEvent->conn_id();
      auto file_from = cache_partial_->GetFile(filename.str());
      auto file_to = cache_complete_->GetFile(filename.str());
      file_from->MoveContentsTo(file_to);

      auto details = network_cache_.GetDetails(httpEvent->conn_id());
      details->response.body_path = file_to->path();
      details->end_timestamp = httpEvent->timestamp();
    }

    break;

    case proto::HttpEventRequest::ABORTED: {
      std::stringstream filename;
      filename << httpEvent->conn_id();
      cache_partial_->GetFile(filename.str())->Delete();

      auto details = network_cache_.GetDetails(httpEvent->conn_id());
      details->end_timestamp = httpEvent->timestamp();
      // TODO: Somehow mark the connection as aborted?
    } break;

    default:
      Log::V("Unhandled http event (%d)", httpEvent->event());
  }

  return Status::OK;
}

void InternalNetworkServiceImpl::JanitorThread() {
  SetThreadName("NetJanitor");

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
