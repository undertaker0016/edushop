package com.edushop;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AdminCatalogCrudTests {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
          .JwtRequestPostProcessor
      admin() {
    return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  @Test
  void categoryCreateEditAndHideKeepsCourseRelations() throws Exception {
    String slug = "crud-" + UUID.randomUUID();
    mvc.perform(
            post("/api/v1/admin/catalog/categories")
                .with(admin())
                .contentType("application/json")
                .content("{\"name\":\"Test category\",\"slug\":\"" + slug + "\",\"sortOrder\":1}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.active").value(true));
    long category = jdbc.queryForObject("SELECT id FROM categories WHERE slug=?", Long.class, slug);
    jdbc.update(
        "INSERT INTO users(full_name,email,password_hash) VALUES('CRUD',?,'unused')",
        slug + "@example.test");
    long user =
        jdbc.queryForObject(
            "SELECT id FROM users WHERE email=?", Long.class, slug + "@example.test");
    jdbc.update(
        "INSERT INTO courses(created_by_user_id,title,slug,status) VALUES(?,'CRUD',?,'DRAFT')",
        user,
        slug);
    long course = jdbc.queryForObject("SELECT id FROM courses WHERE slug=?", Long.class, slug);
    jdbc.update(
        "INSERT INTO course_categories(course_id,category_id) VALUES(?,?)", course, category);
    mvc.perform(
            put("/api/v1/admin/catalog/categories/" + slug)
                .with(admin())
                .contentType("application/json")
                .content("{\"name\":\"Updated\",\"slug\":\"" + slug + "-new\",\"sortOrder\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated"));
    mvc.perform(
            patch("/api/v1/admin/catalog/categories/" + slug + "-new/active?active=false")
                .with(admin()))
        .andExpect(status().isNoContent());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM course_categories WHERE course_id=? AND category_id=?",
                Long.class,
                course,
                category))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT is_active FROM categories WHERE id=?", Boolean.class, category))
        .isFalse();
  }

  @Test
  void usedTagRenamePreservesRelationAndDeleteIsBlocked() throws Exception {
    String name = "tag-" + UUID.randomUUID().toString().substring(0, 8);
    mvc.perform(
            post("/api/v1/admin/catalog/tags")
                .with(admin())
                .contentType("application/json")
                .content("{\"name\":\"" + name + "\"}"))
        .andExpect(status().isCreated());
    long tag = jdbc.queryForObject("SELECT id FROM tags WHERE name=?", Long.class, name);
    jdbc.update(
        "INSERT INTO users(full_name,email,password_hash) VALUES('CRUD',?,'unused')",
        name + "@example.test");
    long user =
        jdbc.queryForObject(
            "SELECT id FROM users WHERE email=?", Long.class, name + "@example.test");
    jdbc.update(
        "INSERT INTO courses(created_by_user_id,title,slug,status) VALUES(?,'CRUD',?,'DRAFT')",
        user,
        name);
    long course = jdbc.queryForObject("SELECT id FROM courses WHERE slug=?", Long.class, name);
    jdbc.update("INSERT INTO course_tags(course_id,tag_id) VALUES(?,?)", course, tag);
    mvc.perform(
            put("/api/v1/admin/catalog/tags/" + name)
                .with(admin())
                .contentType("application/json")
                .content("{\"name\":\"" + name + "-new\"}"))
        .andExpect(status().isOk());
    mvc.perform(delete("/api/v1/admin/catalog/tags/" + name + "-new").with(admin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("khóa học")));
    assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM course_tags WHERE tag_id=?", Long.class, tag))
        .isEqualTo(1);
    jdbc.update("DELETE FROM course_tags WHERE tag_id=?", tag);
    mvc.perform(delete("/api/v1/admin/catalog/tags/" + name + "-new").with(admin()))
        .andExpect(status().isNoContent());
  }

  @Test
  void duplicateTagNameReturnsReadableConflict() throws Exception {
    String name = "duplicate-" + UUID.randomUUID().toString().substring(0, 8);
    jdbc.update("INSERT INTO tags(name) VALUES(?),(?)", name, name + "-other");
    mvc.perform(
            put("/api/v1/admin/catalog/tags/" + name + "-other")
                .with(admin())
                .contentType("application/json")
                .content("{\"name\":\"" + name + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Tên thẻ đã tồn tại."));
  }

  @Test
  void roleAndInputValidationProtectAdminWrites() throws Exception {
    mvc.perform(
            post("/api/v1/admin/catalog/tags")
                .contentType("application/json")
                .content("{\"name\":\"Example\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/admin/catalog/tags")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INSTRUCTOR")))
                .contentType("application/json")
                .content("{\"name\":\"Example\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/admin/catalog/categories")
                .with(admin())
                .contentType("application/json")
                .content("{\"name\":\"Example\",\"slug\":\"bad slug\",\"sortOrder\":-1}"))
        .andExpect(status().isBadRequest());
  }
}
