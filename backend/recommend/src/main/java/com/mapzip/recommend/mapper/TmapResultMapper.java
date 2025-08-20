package com.mapzip.recommend.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mapzip.recommend.dto.MultiSlotRecommendRequestDto;
import com.mapzip.recommend.dto.SlotInfoDto;

public final class TmapResultMapper {

    @SuppressWarnings("unchecked")
    public static MultiSlotRecommendRequestDto toDto(Map<String, Object> map) {
        MultiSlotRecommendRequestDto.MultiSlotRecommendRequestDtoBuilder b =
                MultiSlotRecommendRequestDto.builder()
                        .userId(asString(map.get("userId")))
                        .scheduleId(asString(map.get("scheduleId")))
                        .userNote(asString(map.get("userNote")))
                        .purpose(asString(map.get("purpose")))
                        .companions(asStringList(map.get("companions")))
                        .recommendationRequestIds(asStringList(map.get("recommendationRequestIds")));

        List<Map<String, Object>> slotsMap = (List<Map<String, Object>>) map.get("slots");
        List<SlotInfoDto> slots = new ArrayList<>();
        for (Map<String, Object> s : slotsMap) {
            SlotInfoDto.SlotInfoDtoBuilder sb = SlotInfoDto.builder()
                    .slotId(asString(s.get("slotId")))
                    .lat(asString(s.get("lat")))   
                    .lon(asString(s.get("lon")))  
                    .scheduledTime(asString(s.get("scheduledTime")))
                    .radius(asInt(s.get("radius")));
          

            Object mt = s.get("mealType");
            //sb.mealType(asInt(mt, 0)); // fallback 0

            slots.add(sb.build());
        }
        return b.slots(slots).build();
    }

    private static String asString(Object v) {
        if (v == null) return null;
        if (v instanceof String) return (String) v;
        if (v instanceof Number) {
            // 37.0 -> "37" 같은 꼬리 0 제거
            java.math.BigDecimal bd = new java.math.BigDecimal(((Number) v).toString());
            return bd.stripTrailingZeros().toPlainString();
        }
        return v.toString();
    }

    private static int asInt(Object v) {
        return asInt(v, 0);
    }

    private static int asInt(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt((String) v); } catch (NumberFormatException ignore) {}
        }
        return def;
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object v) {
        if (v instanceof List<?>) {
            List<?> raw = (List<?>) v;
            List<String> out = new ArrayList<>(raw.size());
            for (Object o : raw) out.add(asString(o));
            return out;
        }
        return java.util.Collections.emptyList();
    }

    private TmapResultMapper() {}
}
