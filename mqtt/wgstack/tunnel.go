// Package wgstack is an in-process WireGuard tunnel.
// It opens a UDP socket to the peer and a TCP listener on 127.0.0.1.
// Only connections accepted there are dialed through the tunnel.
package wgstack

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net"
	"net/netip"
	"strings"
	"sync"
	"time"

	"golang.zx2c4.com/wireguard/conn"
	"golang.zx2c4.com/wireguard/device"
	"golang.zx2c4.com/wireguard/tun/netstack"
)

const preferredPort = 51883

type session struct {
	dev    *device.Device
	ln     net.Listener
	cancel context.CancelFunc
	ipc    string
	broker string
	port   int32
}

var (
	mu      sync.Mutex
	current *session
	lastErr string
)

// Start brings the tunnel up and listens on 127.0.0.1.
// addresses and dns are comma-separated IP literals. broker is host:port inside the tunnel.
// The same ipc and broker keep the already open port.
func Start(ipc string, addresses string, dns string, mtu int32, broker string) (int32, error) {
	mu.Lock()
	defer mu.Unlock()
	if current != nil && current.ipc == ipc && current.broker == broker {
		return current.port, nil
	}
	stopLocked()
	opened, err := openDevice(ipc, addresses, dns, int(mtu))
	if err != nil {
		return 0, err
	}
	ln, err := listenLocal()
	if err != nil {
		opened.dev.Close()
		return 0, errors.New("локальный порт WireGuard занят")
	}
	ctx, cancel := context.WithCancel(context.Background())
	port := ln.Addr().(*net.TCPAddr).Port
	current = &session{
		dev:    opened.dev,
		ln:     ln,
		cancel: cancel,
		ipc:    ipc,
		broker: broker,
		port:   int32(port),
	}
	go acceptLoop(ctx, ln, opened.net, broker)
	return current.port, nil
}

// Stop closes the tunnel. Safe to call when it is already down.
func Stop() {
	mu.Lock()
	defer mu.Unlock()
	stopLocked()
}

func LastError() string {
	mu.Lock()
	defer mu.Unlock()
	return lastErr
}

type openedDevice struct {
	dev *device.Device
	net *netstack.Net
}

func openDevice(ipc string, addresses string, dns string, mtu int) (*openedDevice, error) {
	locals, err := parseAddrs(addresses)
	if err != nil || len(locals) == 0 {
		return nil, errors.New("нет адреса туннеля")
	}
	resolvers, err := parseAddrs(dns)
	if err != nil {
		return nil, errors.New("DNS в файле WireGuard должен быть IP-адресом")
	}
	if mtu <= 0 {
		mtu = 1420
	}
	tun, tnet, err := netstack.CreateNetTUN(locals, resolvers, mtu)
	if err != nil {
		return nil, errors.New("не удалось создать туннель")
	}
	dev := device.NewDevice(tun, conn.NewDefaultBind(), device.NewLogger(device.LogLevelError, ""))
	if err := dev.IpcSet(ipc); err != nil {
		dev.Close()
		return nil, errors.New("конфигурация WireGuard отклонена")
	}
	if err := dev.Up(); err != nil {
		dev.Close()
		return nil, errors.New("туннель WireGuard не запустился")
	}
	return &openedDevice{dev: dev, net: tnet}, nil
}

func listenLocal() (net.Listener, error) {
	ln, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", preferredPort))
	if err == nil {
		return ln, nil
	}
	return net.Listen("tcp", "127.0.0.1:0")
}

func acceptLoop(ctx context.Context, ln net.Listener, tnet *netstack.Net, broker string) {
	for {
		client, err := ln.Accept()
		if err != nil {
			return
		}
		go forward(ctx, client, tnet, broker)
	}
}

func forward(ctx context.Context, client net.Conn, tnet *netstack.Net, broker string) {
	defer client.Close()
	dialCtx, cancel := context.WithTimeout(ctx, 12*time.Second)
	defer cancel()
	remote, err := tnet.DialContext(dialCtx, "tcp", broker)
	if err != nil {
		setLastError(err.Error())
		return
	}
	defer remote.Close()
	pipe(client, remote)
}

func pipe(left, right net.Conn) {
	var group sync.WaitGroup
	group.Add(2)
	go func() {
		defer group.Done()
		_, _ = io.Copy(left, right)
		_ = left.Close()
	}()
	go func() {
		defer group.Done()
		_, _ = io.Copy(right, left)
		_ = right.Close()
	}()
	group.Wait()
}

func parseAddrs(csv string) ([]netip.Addr, error) {
	csv = strings.TrimSpace(csv)
	if csv == "" {
		return nil, nil
	}
	parts := strings.Split(csv, ",")
	out := make([]netip.Addr, 0, len(parts))
	for _, part := range parts {
		part = strings.TrimSpace(part)
		if part == "" {
			continue
		}
		addr, err := netip.ParseAddr(part)
		if err != nil {
			return nil, fmt.Errorf("не адрес: %s", part)
		}
		out = append(out, addr)
	}
	if len(out) == 0 {
		return nil, errors.New("нет адреса туннеля")
	}
	return out, nil
}

func stopLocked() {
	if current == nil {
		return
	}
	current.cancel()
	_ = current.ln.Close()
	current.dev.Close()
	current = nil
}

func setLastError(text string) {
	mu.Lock()
	lastErr = text
	mu.Unlock()
}
