package com.example.carbon_credit.DTO;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@Data
public class ProjectUploadDto {

    private String projectName;

    private String description;

    private String location;

    private String vintage;

    private String receiver;

    private BigDecimal carbonAmount;
    private MultipartFile imageFile;
    private MultipartFile docFile;

}
