package com.mapzip.schedule.dto.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Document {
    private String id;
    @JsonProperty("place_name")
    private String placeName;
    @JsonProperty("category_name")
    private String categoryName;
    private String phone;
    @JsonProperty("address_name")
    private String addressName;
    @JsonProperty("road_address_name")
    private String roadAddressName;
    @JsonProperty("x")
    private String longitude; // 경도
    @JsonProperty("y")
    private String latitude;  // 위도
    @JsonProperty("place_url")
    private String placeUrl;
    private String distance;
}
