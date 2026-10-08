package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linj6.interviewagent.common.exception.BusinessException;
import com.linj6.interviewagent.interview.domain.InterviewErrorCode;
import com.linj6.interviewagent.position.infrastructure.persistence.mapper.PositionMapper;
import com.linj6.interviewagent.position.infrastructure.persistence.repository.PositionRepository;
import com.linj6.interviewagent.profile.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class InterviewProfileTest {
    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    InterviewProfileApplicationService application;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @SpyBean
    PositionRepository positions;

    @SpyBean
    PositionMapper positionMapper;

    @BeforeEach
    void resetData() {
        jdbc.update("DELETE FROM candidate_project");
        jdbc.update("UPDATE candidate SET name='候选人' WHERE id=1");
        jdbc.update("UPDATE candidate_resume SET id=77, summary='简历' WHERE candidate_id=1");
        jdbc.update(
                "INSERT INTO candidate_project(id,resume_id,name,description) VALUES(101,77,'项目一','描述一'),(102,77,'项目二','描述二'),(999,88,'其他简历项目','其他')");
        jdbc.update("UPDATE position SET title='岗位', description='岗位描述' WHERE id=1");
    }

    InterviewProfileRequest request(List<InterviewProfileRequest.ProjectRequest> projects) {
        return new InterviewProfileRequest("新姓名", "新简历", projects, "新岗位", "新岗位描述");
    }

    InterviewProfileRequest.ProjectRequest project(Long id, String name) {
        return new InterviewProfileRequest.ProjectRequest(id, name, "新项目描述");
    }

    @Test
    void getReturnsCompleteProfileAndIds() throws Exception {
        mvc.perform(get("/api/interview-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.candidateName").value("候选人"))
                .andExpect(jsonPath("$.data.resumeSummary").value("简历"))
                .andExpect(jsonPath("$.data.positionTitle").value("岗位"))
                .andExpect(jsonPath("$.data.positionDescription").value("岗位描述"))
                .andExpect(jsonPath("$.data.projects.length()").value(2))
                .andExpect(jsonPath("$.data.projects[0].id").value("101"))
                .andExpect(jsonPath("$.data.projects[0].description").value("描述一"))
                .andExpect(jsonPath("$.data.projects[1].id").value("102"));
    }

    @Test
    void replacesScalarsAndReconcilesByIdWithoutNameMatching() throws Exception {
        var replacement = request(List.of(project(101L, "已修改"), project(null, "项目二")));
        var result = mvc.perform(put("/api/interview-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(replacement)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidateName").value("新姓名"))
                .andExpect(jsonPath("$.data.resumeSummary").value("新简历"))
                .andExpect(jsonPath("$.data.positionTitle").value("新岗位"))
                .andExpect(jsonPath("$.data.positionDescription").value("新岗位描述"))
                .andReturn();
        var response = json.readValue(
                json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                        .get("data")
                        .toString(),
                InterviewProfileResponse.class);
        assertThat(response).isEqualTo(application.getProfile());
        assertThat(response.projects()).hasSize(2);
        assertThat(response.projects().get(0).id()).isEqualTo(101L);
        assertThat(response.projects().get(0).name()).isEqualTo("已修改");
        Long added = response.projects().get(1).id();
        assertThat(added).isNotNull().isNotIn(101L, 102L, 999L);
        assertThat(jdbc.queryForObject("SELECT resume_id FROM candidate_project WHERE id=?", Long.class, added))
                .isEqualTo(77L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM candidate_project WHERE id=102", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT name FROM candidate_project WHERE id=999", String.class))
                .isEqualTo("其他简历项目");
    }

    @Test
    void acceptsZeroAndFiveProjects() {
        assertThat(application.replaceProfile(request(List.of())).projects()).isEmpty();
        var five = IntStream.range(0, 5).mapToObj(i -> project(null, "项目" + i)).toList();
        var response = application.replaceProfile(request(five));
        assertThat(response.projects()).hasSize(5);
        assertThat(response.projects())
                .extracting(InterviewProfileResponse.ProjectResponse::id)
                .doesNotHaveDuplicates()
                .doesNotContainNull();
    }

    @Test
    void rejectsSixDuplicateUnknownAndForeignIdsWithoutWrites() throws Exception {
        var invalid = List.of(
                request(IntStream.range(0, 6)
                        .mapToObj(i -> project(null, "项目" + i))
                        .toList()),
                request(List.of(project(101L, "一"), project(101L, "二"))),
                request(List.of(project(123456L, "未知"))),
                request(List.of(project(999L, "其他简历"))));
        var before = application.getProfile();
        for (var r : invalid) {
            mvc.perform(put("/api/interview-profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(r)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(10001));
            assertThat(application.getProfile()).isEqualTo(before);
        }
    }

    @Test
    void validatesRequiredFieldsAndSchemaLengthsBeforeWrites() throws Exception {
        var before = application.getProfile();
        var invalid = List.of(
                new InterviewProfileRequest("x".repeat(101), "简历", List.of(), "岗位", "描述"),
                new InterviewProfileRequest("名字", "简历", List.of(), "x".repeat(201), "描述"),
                new InterviewProfileRequest("名字", null, List.of(), "岗位", "描述"),
                new InterviewProfileRequest("名字", "简历", null, "岗位", "描述"),
                request(List.of(new InterviewProfileRequest.ProjectRequest(null, "x".repeat(201), "描述"))),
                request(List.of(new InterviewProfileRequest.ProjectRequest(null, "名字", null))),
                new InterviewProfileRequest("名字", "中".repeat(21846), List.of(), "岗位", "描述"));
        for (var r : invalid) {
            mvc.perform(put("/api/interview-profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(r)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(10001));
            assertThat(application.getProfile()).isEqualTo(before);
        }
    }

    @Test
    void rollsBackCandidateResumeProjectsAndPositionOnLateFailure() {
        var before = application.getProfile();
        var rows = jdbc.queryForList("SELECT * FROM candidate_project ORDER BY id");
        doAnswer(invocation -> {
                    invocation.callRealMethod();
                    assertThat(jdbc.queryForObject("SELECT name FROM candidate WHERE id=1", String.class))
                            .isEqualTo("新姓名");
                    assertThat(jdbc.queryForObject("SELECT title FROM position WHERE id=1", String.class))
                            .isEqualTo("新岗位");
                    throw new IllegalStateException("test failure after all writes");
                })
                .when(positions)
                .replaceProfile(anyLong(), any());
        assertThatThrownBy(() -> application.replaceProfile(request(List.of(project(101L, "修改"), project(null, "新增")))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(application.getProfile()).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM candidate_project ORDER BY id"))
                .isEqualTo(rows);
    }

    @Test
    void mapsMissingCurrentPositionToExistingConsistencyErrorForGetAndPut() throws Exception {
        jdbc.update("DELETE FROM position WHERE id=1");
        try {
            mvc.perform(get("/api/interview-profile"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(23011));
            mvc.perform(put("/api/interview-profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(request(List.of()))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(23011));
            assertThat(jdbc.queryForObject("SELECT name FROM candidate WHERE id=1", String.class))
                    .isEqualTo("候选人");
        } finally {
            jdbc.update("INSERT INTO position (id,title,description) VALUES(1,'岗位','岗位描述')");
        }
    }

    @Test
    void rollsBackAggregateWhenPositionUpdateReturnsZeroRows() {
        var before = application.getProfile();
        var rows = jdbc.queryForList("SELECT * FROM candidate_project ORDER BY id");
        doReturn(0).when(positionMapper).update(isNull(), any(Wrapper.class));
        assertThatThrownBy(() -> application.replaceProfile(request(List.of(project(101L, "修改"), project(null, "新增")))))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(InterviewErrorCode.INTERVIEW_DATA_INCONSISTENT));
        assertThat(application.getProfile()).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM candidate_project ORDER BY id"))
                .isEqualTo(rows);
    }
}
