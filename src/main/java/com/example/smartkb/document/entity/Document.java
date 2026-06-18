package com.example.smartkb.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_document")
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kbId;

    private String fileName;

    private String fileType;

    private Long fileSize;

    private String minioPath;

    private Integer chunkCount;

    private Integer status;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}

