package com.plocky.domain.plogging.service;

import com.plocky.domain.member.entity.Member;
import com.plocky.domain.member.repository.MemberRepository;
import com.plocky.domain.plogging.dto.CreatePloggingDto;
import com.plocky.domain.plogging.dto.kakaoMap.KakaoMapDocument;
import com.plocky.domain.plogging.dto.kakaoMap.KakaoMapResponse;
import com.plocky.domain.plogging.dto.kakaoMap.KakaoMapRoadAddress;
import com.plocky.domain.plogging.entity.Plogging;
import com.plocky.domain.plogging.repository.LocationRepository;
import com.plocky.domain.plogging.repository.PloggingRepository;
import com.plocky.domain.trashCategory.entity.TrashCategory;
import com.plocky.domain.trashCategory.repository.TrashCategoryRepository;
import com.plocky.global.entity.Location;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import org.springframework.http.HttpHeaders;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PloggingService {
    private final PloggingRepository ploggingRepository;
    private final MemberRepository memberRepository;
    private final TrashCategoryRepository trashCategoryRepository;
    private final LocationRepository locationRepository;
    private final RestTemplate restTemplate;
    @Value("${kakao.client.id}")
    private String REST_API_KEY;

    // 위도, 경도로 주소 구하기
    private KakaoMapResponse requestKakaoMap(float lat, float lon) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK "+REST_API_KEY);
        headers.set("content-type", "application/json;charset=UTF-8");
        HttpEntity<String> requestEntity = new HttpEntity<>(null, headers);
        return restTemplate.exchange("https://dapi.kakao.com/v2/local/geo/coord2address.json?x="+lon+"&y="+lat,
                HttpMethod.GET, requestEntity, KakaoMapResponse.class).getBody();
    }

    // 주소에서 정보 두개만 빼오기 (ex. 서울특별시 + ㅇㅇ구)
    private String findAddress(float lat, float lon) {
        KakaoMapResponse response = requestKakaoMap(lat, lon);
        List<KakaoMapDocument> documents = response.getDocuments();
        if (documents != null && !documents.isEmpty()) {
            KakaoMapRoadAddress address = documents.get(0).getRoad_address();
            StringBuilder responseAddress = new StringBuilder();
            if (address.getRegion_1depth_name() != null) {
                responseAddress.append(address.getRegion_1depth_name());
                if (address.getRegion_2depth_name() != null) {
                    responseAddress.append(" " + address.getRegion_2depth_name()); }
            }
            return responseAddress.toString();
        }
        return "경기도 성남시 분당구";
    }

    // 위치 정보 구하기
    private Location createLocation(float lat, float lon){
        String address = findAddress(lat, lon);
        Location location = Location.builder()
                .latitude(lat)
                .longitude(lon)
                .address(address)
                .build();
        return locationRepository.save(location);
    }

    // trashCategory 생성하기
    private TrashCategory createTrashCategory(CreatePloggingDto form){
        TrashCategory trashCategory = TrashCategory.builder()
                .paperQuantity(form.getTrash().getPaperQuantity())
                .plaQuantity(form.getTrash().getPlaQuantity())
                .glassQuantity(form.getTrash().getGlassQuantity())
                .canQuantity(form.getTrash().getCanQuantity())
                .foamQuantity(form.getTrash().getFoamQuantity())
                .etcQuantity(form.getTrash().getEtcQuantity())
                .cigarQuantity(form.getTrash().getCigarQuantity())
                .build();
        return trashCategoryRepository.save(trashCategory);
    }

    public String create(CreatePloggingDto form, String kakaoId) {
        // 사용자 구하기
        Member member = memberRepository.findByKakaoId(kakaoId).orElseThrow(
                () -> new NullPointerException("Member not found for kakaoId: " + kakaoId));

        // 위도, 경도로 구 주소 구하기
        Location startedLocation = createLocation(form.getStartedLatitude(), form.getStartedLongitude());
        Location endedLocation = createLocation(form.getEndedLatitude(), form.getEndedLongitude());

        // trashCategory 생성하기
        TrashCategory trashCategory = createTrashCategory(form);
        int totalQuantity = trashCategory.getTotalQuantity();

        //plogging 생성 및 저장하기
        Plogging newPlogging = Plogging.builder()
                .distance(form.getDistance())
                .quantity(totalQuantity)
                .startedLocation(startedLocation)
                .endedLocation(endedLocation)
                .startedWhen(form.getStartedAt())
                .endedWhen(form.getEndedAt())
                .trashCategory(trashCategory)
                .member(member)
                .build();
        ploggingRepository.save(newPlogging);

        // member에 플로깅 정보 추가
        member.addTotalDistance(newPlogging.getDistance());
        member.addTotalQuantity(newPlogging.getQuantity());

        // dto에 정보 담기
        return newPlogging.getId().toString();
    }
}
