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

package org.apache.inlong.manager.common.pojo.workflow.form;

import com.google.common.collect.Maps;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.inlong.manager.common.enums.OperateType;
import org.apache.inlong.manager.common.exceptions.FormValidateException;
import org.apache.inlong.manager.common.pojo.group.InlongGroupInfo;
import org.apache.inlong.manager.common.pojo.stream.InlongStreamInfo;
import org.apache.inlong.manager.common.util.Preconditions;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
public class UpdateGroupProcessForm extends BaseProcessForm {

    public static final String FORM_NAME = "UpdateGroupProcessForm";
    @ApiModelProperty(value = "Inlong group info", required = true)
    private InlongGroupInfo groupInfo;
    @Getter
    @Setter
    @ApiModelProperty(value = "OperateType to define the update operation", required = true)
    private OperateType operateType;

    private List<InlongStreamInfo> streamList;

    @Override
    public void validate() throws FormValidateException {
        Preconditions.checkNotNull(groupInfo, "inlong group info is empty");
        Preconditions.checkNotNull(operateType, "operate type is empty");
    }

    @Override
    public String getFormName() {
        return FORM_NAME;
    }

    @Override
    public String getInlongGroupId() {
        return groupInfo.getInlongGroupId();
    }

    @Override
    public Map<String, Object> showInList() {
        Map<String, Object> show = Maps.newHashMap();
        show.put("inlongGroupId", groupInfo.getInlongGroupId());
        show.put("operateType", operateType.name().toLowerCase(Locale.ROOT));
        return show;
    }
}
