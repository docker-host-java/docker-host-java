package com.github.dockerhostjava.connection.SshConnection.logic;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;

/** 인증을 마친 SSH 클라이언트와 세션 묶음. */
public record AuthenticatedSession(SshClient client, ClientSession session) {
}
