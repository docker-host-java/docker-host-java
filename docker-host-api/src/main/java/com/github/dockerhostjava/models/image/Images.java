package com.github.dockerhostjava.models.image;

import com.github.dockerhostjava.models.shared.ImageRef;

import java.util.List;
import java.util.Optional;

public interface Images {

    // 조회
    Image get(ImageRef ref);           // 없으면 ImageNotFoundException
    Optional<Image> find(ImageRef ref);
    boolean exists(ImageRef ref);
    List<Image> list();
//    List<Image>      list(검색 가능 객체);   // dangling, label, reference, since

    // 획득 (로컬에 없던 이미지를 만들어내는 연산)
    Image pull(ImageRef ref);
    Image build();
    List<Image> load();

}
