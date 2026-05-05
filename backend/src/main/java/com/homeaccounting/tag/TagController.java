package com.homeaccounting.tag;

import com.homeaccounting.api.dto.ApiResponse;
import com.homeaccounting.api.dto.NameBody;
import com.homeaccounting.auth.CurrentUser;
import com.homeaccounting.entity.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

  private final TagService tagService;

  public TagController(TagService tagService) {
    this.tagService = tagService;
  }

  @GetMapping
  public ApiResponse<List<Tag>> list(HttpServletRequest request) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(tagService.list(uid));
  }

  @GetMapping("/{id}")
  public ApiResponse<Tag> get(HttpServletRequest request, @PathVariable long id) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(tagService.get(uid, id));
  }

  @PostMapping
  public ApiResponse<Tag> create(HttpServletRequest request, @Valid @RequestBody NameBody body) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(tagService.create(uid, body.name()));
  }

  @PutMapping("/{id}")
  public ApiResponse<Tag> update(
      HttpServletRequest request, @PathVariable long id, @Valid @RequestBody NameBody body) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(tagService.update(uid, id, body.name()));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(HttpServletRequest request, @PathVariable long id) {
    long uid = CurrentUser.requireUserId(request);
    tagService.delete(uid, id);
    return ApiResponse.ok();
  }
}
