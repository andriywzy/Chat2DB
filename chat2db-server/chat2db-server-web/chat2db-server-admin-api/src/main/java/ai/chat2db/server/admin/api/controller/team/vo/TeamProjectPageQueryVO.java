package ai.chat2db.server.admin.api.controller.team.vo;

import ai.chat2db.server.admin.api.controller.project.vo.SimpleProjectVO;
import ai.chat2db.server.common.api.controller.vo.SimpleEnvironmentVO;
import lombok.Data;

import java.util.List;

@Data
public class TeamProjectPageQueryVO {

    private Long id;

    private Long teamId;

    private SimpleProjectVO project;

    private List<SimpleEnvironmentVO> environmentList;
}
