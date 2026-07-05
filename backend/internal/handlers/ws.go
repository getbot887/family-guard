package handlers

import (
	"log"
	"net/http"
	"sync"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/gorilla/websocket"
)

var upgrader = websocket.Upgrader{
	CheckOrigin: func(r *http.Request) bool { return true },
}

// WebSocket 连接池，按 device_id 分组
type WSHub struct {
	mu      sync.RWMutex
	devices map[int]map[*websocket.Conn]bool // deviceID → 连接集合
}

var Hub = &WSHub{devices: make(map[int]map[*websocket.Conn]bool)}

func (h *WSHub) Register(deviceID int, conn *websocket.Conn) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if h.devices[deviceID] == nil {
		h.devices[deviceID] = make(map[*websocket.Conn]bool)
	}
	h.devices[deviceID][conn] = true
	log.Printf("WebSocket 设备 %d 已连接，当前连接数: %d", deviceID, len(h.devices[deviceID]))
}

func (h *WSHub) Unregister(deviceID int, conn *websocket.Conn) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if conns, ok := h.devices[deviceID]; ok {
		delete(conns, conn)
		conn.Close()
		if len(conns) == 0 {
			delete(h.devices, deviceID)
		}
	}
}

// Broadcast 向指定设备推送消息
func (h *WSHub) Broadcast(deviceID int, msg []byte) {
	h.mu.RLock()
	defer h.mu.RUnlock()
	for conn := range h.devices[deviceID] {
		if err := conn.WriteMessage(websocket.TextMessage, msg); err != nil {
			log.Printf("WebSocket 推送失败 device=%d: %v", deviceID, err)
			conn.Close()
			delete(h.devices[deviceID], conn)
		}
	}
}

// NotifyRulesUpdated 通知设备规则已更新
func (h *WSHub) NotifyRulesUpdated(deviceIDs []int) {
	h.sendEvent("rules_updated", deviceIDs)
}

// NotifyLock 通知设备锁屏
func (h *WSHub) NotifyLock(deviceID int) {
	h.sendEvent("lock", []int{deviceID})
}

func (h *WSHub) sendEvent(event string, deviceIDs []int) {
	msg := []byte(`{"event":"` + event + `","timestamp":"` + time.Now().Format(time.RFC3339) + `"}`)
	if len(deviceIDs) == 0 {
		h.mu.RLock()
		defer h.mu.RUnlock()
		for did := range h.devices {
			for conn := range h.devices[did] {
				conn.WriteMessage(websocket.TextMessage, msg)
			}
		}
		return
	}
	for _, did := range deviceIDs {
		h.Broadcast(did, msg)
	}
}

// ChildWS WebSocket 端点：孩子端连接，接收实时推送
func (h *Handler) ChildWS(c *gin.Context) {
	deviceID := c.GetInt("user_id")
	conn, err := upgrader.Upgrade(c.Writer, c.Request, nil)
	if err != nil {
		log.Printf("WebSocket 升级失败 device=%d: %v", deviceID, err)
		return
	}
	Hub.Register(deviceID, conn)
	defer Hub.Unregister(deviceID, conn)

	// 保持连接，读取客户端消息（用于检测断开）
	for {
		_, _, err := conn.ReadMessage()
		if err != nil {
			break
		}
	}
}
