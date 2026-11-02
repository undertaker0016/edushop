package com.edushop.service;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CatalogDiscoveryService {
  private final JdbcTemplate jdbc;

  public CatalogDiscoveryService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String FROM =
      " "
          + """
FROM courses c
LEFT JOIN (SELECT course_id,MIN(price) AS minPrice FROM course_offerings WHERE status='OPEN' AND start_date>=CURRENT_DATE AND COALESCE(registration_closes_at,TIMESTAMP(start_date))>CURRENT_TIMESTAMP GROUP BY course_id) p ON p.course_id=c.id
LEFT JOIN (SELECT course_id,AVG(rating) AS rating,COUNT(*) AS reviewCount FROM reviews WHERE is_hidden=FALSE GROUP BY course_id) r ON r.course_id=c.id
LEFT JOIN (SELECT o.course_id,COUNT(DISTINCT e.user_id) AS studentCount FROM enrollments e JOIN course_offerings o ON o.id=e.offering_id WHERE e.status='ACTIVE' GROUP BY o.course_id) e ON e.course_id=c.id
""";

  public Map<String, Object> search(
      int page,
      int size,
      String q,
      String category,
      String tag,
      String sort,
      BigDecimal minPrice,
      BigDecimal maxPrice,
      Double minRating) {
    if (page < 0
        || size < 1
        || size > 100
        || (q != null && q.length() > 100)
        || (category != null && category.length() > 100)
        || (tag != null && tag.length() > 50)
        || (minPrice != null && minPrice.signum() < 0)
        || (maxPrice != null && maxPrice.signum() < 0)
        || (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
        || (minRating != null && (!Double.isFinite(minRating) || minRating < 0 || minRating > 5)))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bộ lọc không hợp lệ.");
    var args = new ArrayList<Object>();
    String where = " WHERE c.status='PUBLISHED'";
    if (q != null && !q.isBlank()) {
      where += " AND (c.title LIKE ? OR c.description LIKE ?)";
      args.add("%" + q.trim() + "%");
      args.add("%" + q.trim() + "%");
    }
    if (category != null && !category.isBlank()) {
      where +=
          " AND EXISTS(SELECT 1 FROM course_categories cc JOIN categories ca ON"
              + " ca.id=cc.category_id WHERE cc.course_id=c.id AND ca.slug=? AND"
              + " ca.is_active=TRUE)";
      args.add(category);
    }
    if (tag != null && !tag.isBlank()) {
      where +=
          " AND EXISTS(SELECT 1 FROM course_tags ct JOIN tags t ON t.id=ct.tag_id WHERE"
              + " ct.course_id=c.id AND t.name=?)";
      args.add(tag);
    }
    if (minPrice != null) {
      where += " AND p.minPrice>=?";
      args.add(minPrice);
    }
    if (maxPrice != null) {
      where += " AND p.minPrice<=?";
      args.add(maxPrice);
    }
    if (minRating != null) {
      where += " AND COALESCE(r.rating,0)>=?";
      args.add(minRating);
    }
    String ordering =
        switch (Objects.toString(sort, "newest")) {
          case "newest" -> "c.created_at DESC,c.id DESC";
          case "price_asc" -> "p.minPrice IS NULL,p.minPrice ASC,c.id DESC";
          case "price_desc" -> "p.minPrice IS NULL,p.minPrice DESC,c.id DESC";
          case "rating_desc" ->
              "COALESCE(r.rating,0) DESC,COALESCE(r.reviewCount,0) DESC,c.id DESC";
          case "students_desc" -> "COALESCE(e.studentCount,0) DESC,c.id DESC";
          case "title_asc" -> "c.title ASC,c.id DESC";
          default ->
              throw new ResponseStatusException(
                  HttpStatus.BAD_REQUEST, "Kiểu sắp xếp không hợp lệ.");
        };
    long count = jdbc.queryForObject("SELECT COUNT(*)" + FROM + where, Long.class, args.toArray());
    args.add(size);
    args.add((long) page * size);
    var items =
        jdbc.queryForList(
            "SELECT c.id AS courseId,c.slug,c.title,c.description,c.thumbnail_url AS"
                + " thumbnailUrl,p.minPrice,COALESCE(r.rating,0) AS"
                + " rating,COALESCE(r.reviewCount,0) AS reviewCount,COALESCE(e.studentCount,0) AS"
                + " studentCount"
                + FROM
                + where
                + " ORDER BY "
                + ordering
                + " LIMIT ? OFFSET ?",
            args.toArray());
    return Map.of(
        "items",
        items,
        "page",
        page,
        "size",
        size,
        "totalElements",
        count,
        "totalPages",
        (count + size - 1) / size);
  }

  public Map<String, Object> metrics(String slug) {
    var rows =
        jdbc.queryForList(
            "SELECT c.id AS courseId,p.minPrice,COALESCE(r.rating,0) AS"
                + " rating,COALESCE(r.reviewCount,0) AS reviewCount,COALESCE(e.studentCount,0) AS"
                + " studentCount"
                + FROM
                + " WHERE c.slug=? AND c.status='PUBLISHED'",
            slug);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return rows.getFirst();
  }
}
