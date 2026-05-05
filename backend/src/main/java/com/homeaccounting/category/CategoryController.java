package com.homeaccounting.category;

import com.homeaccounting.api.dto.ApiResponse;
import com.homeaccounting.auth.CurrentUser;
import com.homeaccounting.entity.Category;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

  private final CategoryService categoryService;

  public CategoryController(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  /** 可选 query：type=EXPENSE | INCOME */
  @GetMapping
  public ApiResponse<List<Category>> list(
      HttpServletRequest request, @RequestParam(required = false) String type) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(categoryService.list(uid, type));
  }

  @GetMapping("/{id}")
  public ApiResponse<Category> get(HttpServletRequest request, @PathVariable long id) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(categoryService.get(uid, id));
  }

  @PostMapping
  public ApiResponse<Category> create(
      HttpServletRequest request, @Valid @RequestBody CreateCategoryBody body) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(
        categoryService.create(
            uid, body.type(), body.name(), body.sortOrder(), body.enabled()));
  }

  @PutMapping("/{id}")
  public ApiResponse<Category> update(
      HttpServletRequest request,
      @PathVariable long id,
      @Valid @RequestBody UpdateCategoryBody body) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(
        categoryService.update(uid, id, body.name(), body.sortOrder(), body.enabled()));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(HttpServletRequest request, @PathVariable long id) {
    long uid = CurrentUser.requireUserId(request);
    categoryService.delete(uid, id);
    return ApiResponse.ok();
  }

  public record CreateCategoryBody(
      @NotBlank @Size(max = 16) String type,
      @NotBlank @Size(max = 64) String name,
      Integer sortOrder,
      Boolean enabled) {}

  public record UpdateCategoryBody(
      @Size(max = 64) String name, Integer sortOrder, Boolean enabled) {}
}
