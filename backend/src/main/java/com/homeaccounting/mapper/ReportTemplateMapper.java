package com.homeaccounting.mapper;

import com.homeaccounting.entity.ReportTemplate;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ReportTemplateMapper {

  int insert(ReportTemplate row);

  ReportTemplate selectByPrimaryKey(Long id);

  List<ReportTemplate> selectByHouseholdId(@Param("householdId") long householdId);

  int updateByPrimaryKeySelective(ReportTemplate row);

  int deleteByPrimaryKey(Long id);

  ReportTemplate selectByHouseholdIdAndName(
      @Param("householdId") long householdId, @Param("name") String name);
}
