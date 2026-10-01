package com.github.dockerhostjava.models.image;

import com.github.dockerhostjava.models.shared.ImageRef;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface Images {

    // 조회
    Image get(ImageRef ref);           // 없으면 ImageNotFoundException
    Image getById(String imageId);     // sha256:... 로 조회, 없으면 ImageNotFoundException
    Optional<Image> find(ImageRef ref);
    boolean exists(ImageRef ref);
    List<Image> list();
//    List<Image>      list(검색 가능 객체);   // dangling, label, reference, since
    List<ImageSearchResult> search(String term);   // 로컬이 아닌 레지스트리(Docker Hub) 검색

    // 로드 (로컬에 없던 이미지를 가져오는 메서드)
    Image pull(ImageRef ref);
    Image build(Dockerfile dockerfile);                    // COPY/ADD를 쓰지 않는 경우
    Image build(Dockerfile dockerfile, Path context);      // context 디렉터리를 빌드 컨텍스트로 전송
    List<Image> load(InputStream tar);                     // docker save로 만든 tar 로드
    Image create(ImageRef repository, InputStream rootfs); // docker import, 파일시스템 tar로 새 이미지 생성

}
