package wgstack

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"io"
	"net"
	"testing"
	"time"

	"golang.org/x/crypto/curve25519"
)

func TestUserspaceTunnelCarriesTCP(t *testing.T) {
	leftPriv, leftPub := mustKey(t)
	rightPriv, rightPub := mustKey(t)
	leftPort := freeUDP(t)
	rightPort := freeUDP(t)

	left, err := openDevice(ipc(leftPriv, rightPub, leftPort, rightPort, "10.88.0.2/32"), "10.88.0.1", "", 1420)
	if err != nil {
		t.Fatal(err)
	}
	defer left.dev.Close()
	right, err := openDevice(ipc(rightPriv, leftPub, rightPort, leftPort, "10.88.0.1/32"), "10.88.0.2", "", 1420)
	if err != nil {
		t.Fatal(err)
	}
	defer right.dev.Close()

	echo, err := right.net.ListenTCP(&net.TCPAddr{IP: net.ParseIP("10.88.0.2"), Port: 9000})
	if err != nil {
		t.Fatal(err)
	}
	defer echo.Close()
	go func() {
		conn, err := echo.Accept()
		if err != nil {
			return
		}
		defer conn.Close()
		_, _ = io.Copy(conn, conn)
	}()

	proxy, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer proxy.Close()
	go acceptLoop(context.Background(), proxy, left.net, "10.88.0.2:9000")

	var conn net.Conn
	deadline := time.Now().Add(8 * time.Second)
	for {
		conn, err = net.DialTimeout("tcp", proxy.Addr().String(), time.Second)
		if err == nil {
			break
		}
		if time.Now().After(deadline) {
			t.Fatalf("dial through tunnel: %v (last %s)", err, LastError())
		}
		time.Sleep(100 * time.Millisecond)
	}
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(5 * time.Second))
	payload := []byte("tbox-mqtt")
	if _, err := conn.Write(payload); err != nil {
		t.Fatal(err)
	}
	buf := make([]byte, len(payload))
	if _, err := io.ReadFull(conn, buf); err != nil {
		t.Fatalf("read echo: %v (last %s)", err, LastError())
	}
	if string(buf) != string(payload) {
		t.Fatalf("echo %q", buf)
	}
}

func ipc(privateHex, peerHex string, listenPort, endpointPort int, allowed string) string {
	return fmt.Sprintf(
		"private_key=%s\nlisten_port=%d\nreplace_peers=true\npublic_key=%s\nendpoint=127.0.0.1:%d\nreplace_allowed_ips=true\nallowed_ip=%s\n",
		privateHex, listenPort, peerHex, endpointPort, allowed,
	)
}

func freeUDP(t *testing.T) int {
	t.Helper()
	conn, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	return conn.LocalAddr().(*net.UDPAddr).Port
}

func mustKey(t *testing.T) (privateHex, publicHex string) {
	t.Helper()
	var secret [32]byte
	if _, err := rand.Read(secret[:]); err != nil {
		t.Fatal(err)
	}
	secret[0] &= 248
	secret[31] = (secret[31] & 127) | 64
	public, err := curve25519.X25519(secret[:], curve25519.Basepoint)
	if err != nil {
		t.Fatal(err)
	}
	return hex.EncodeToString(secret[:]), hex.EncodeToString(public)
}
