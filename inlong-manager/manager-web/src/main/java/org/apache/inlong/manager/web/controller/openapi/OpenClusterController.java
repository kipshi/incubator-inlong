/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.manager.web.controller.openapi;

import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.apache.inlong.manager.common.beans.Response;
import org.apache.inlong.manager.common.enums.OperationType;
import org.apache.inlong.manager.common.pojo.cluster.ClusterPageRequest;
import org.apache.inlong.manager.common.pojo.cluster.ClusterRequest;
import org.apache.inlong.manager.common.pojo.cluster.ClusterResponse;
import org.apache.inlong.manager.service.core.ThirdPartyClusterService;
import org.apache.inlong.manager.service.core.operationlog.OperationLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cluster controller
 */
@RestController
@RequestMapping("/openapi/cluster")
@Api(tags = "Open-Cluster-API")
public class OpenClusterController {

    @Autowired
    private ThirdPartyClusterService thirdPartyClusterService;

    @PostMapping(value = "/save")
    @ApiOperation(value = "Save cluster info")
    @OperationLog(operation = OperationType.CREATE)
    public Response<Integer> save(@RequestBody ClusterRequest request) {
        return Response.success(thirdPartyClusterService.save(request, null));
    }

    @GetMapping(value = "/get/{id}")
    @ApiOperation(value = "Get cluster by id")
    @ApiImplicitParam(name = "id", value = "common cluster ID", dataTypeClass = Integer.class, required = true)
    public Response<ClusterResponse> get(@PathVariable Integer id) {
        return Response.success(thirdPartyClusterService.get(id));
    }

    @PostMapping(value = "/list")
    @ApiOperation(value = "Get clusters by paginating")
    public Response<PageInfo<ClusterResponse>> list(@RequestBody ClusterPageRequest request) {
        return Response.success(thirdPartyClusterService.list(request));
    }

    @PostMapping(value = "/update")
    @OperationLog(operation = OperationType.UPDATE)
    @ApiOperation(value = "Update cluster info")
    public Response<Boolean> update(@RequestBody ClusterRequest request) {
        return Response.success(thirdPartyClusterService.update(request, null));
    }

    @DeleteMapping(value = "/delete/{id}")
    @ApiOperation(value = "Delete cluster by id")
    @OperationLog(operation = OperationType.DELETE)
    @ApiImplicitParam(name = "id", value = "Cluster ID", dataTypeClass = Integer.class, required = true)
    public Response<Boolean> delete(@PathVariable Integer id) {
        return Response.success(thirdPartyClusterService.delete(id, null));
    }

}
