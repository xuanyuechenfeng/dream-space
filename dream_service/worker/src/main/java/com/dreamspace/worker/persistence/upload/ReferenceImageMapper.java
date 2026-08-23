package com.dreamspace.worker.persistence.upload;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReferenceImageMapper {
  @Select("SELECT \"id\",\"userId\",\"objectKey\",\"mimeType\" FROM \"ReferenceUpload\" "
      + "WHERE \"id\"=#{imageId} AND \"userId\"=#{userId} AND \"deletedAt\" IS NULL LIMIT 1")
  ReferenceImageRecord findOwned(@Param("userId") String userId, @Param("imageId") String imageId);
}
