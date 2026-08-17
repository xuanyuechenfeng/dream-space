package com.dreamspace.api.persistence.upload;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReferenceUploadMapper {
  @Insert("INSERT INTO \"ReferenceUpload\" (\"id\",\"userId\",\"objectKey\",\"originalFilename\",\"mimeType\",\"byteSize\",\"width\",\"height\",\"checksumSha256\",\"createdAt\") VALUES (#{id},#{userId},#{objectKey},#{originalFilename},#{mimeType},#{byteSize},#{width},#{height},#{checksumSha256},CURRENT_TIMESTAMP)")
  int insert(ReferenceUploadRecord record);
  @Select("SELECT * FROM \"ReferenceUpload\" WHERE \"id\" = #{id} AND \"userId\" = #{userId} AND \"deletedAt\" IS NULL LIMIT 1")
  ReferenceUploadRecord findOwned(@Param("userId") String userId, @Param("id") String id);
}
