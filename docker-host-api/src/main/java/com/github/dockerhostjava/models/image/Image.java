package com.github.dockerhostjava.models.image;

import com.github.dockerhostjava.models.shared.ImageRef;

import java.io.OutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface Image {

    // 정체성
    String id();                 // sha256:... content-addressed
    Set<ImageRef> tags();        // RepoTags
    Set<ImageRef> digests();     // RepoDigests

    // 메타데이터 (inspect 스냅샷)
    long size();
    Instant createdAt();
//    <?> platform();      // os / arch / variant
//    <?> config();        // Env, Cmd, Entrypoint, ExposedPorts, Labels

    // 조회 (스냅샷 아님)
    List<ImageHistory> history();

    // 상태 변경
    void tag(ImageRef ref);
    void untag(ImageRef ref);
    void push(ImageRef ref); // 추후에 도커 대몬의 api에 반환값을 달는 타입을 만들어야할 듯
    void remove();


    // 컨태이너
//    Container create(); // 생성
//    Container run();    // 생성 + 동작
    // Image 책임이 아님

    void saveTo(OutputStream out);

    Image refresh();  // inspect 재조회
}
