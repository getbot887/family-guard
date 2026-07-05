package handlers

import (
	"database/sql"
	"fmt"
	"math/rand"
	"strconv"
	"time"

	"family-guard-backend/internal/middleware"
	"family-guard-backend/internal/models"
	"family-guard-backend/internal/repository"

	"github.com/gin-gonic/gin"
	"golang.org/x/crypto/bcrypt"
)

type Handler struct {
	repo      *repository.Repository
	jwtSecret string
}

func New(repo *repository.Repository, jwtSecret string) *Handler {
	return &Handler{repo: repo, jwtSecret: jwtSecret}
}

// ===== Auth =====

func (h *Handler) Register(c *gin.Context) {
	var req models.RegisterRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(400, apiErr("请求参数无效: "+err.Error()))
		return
	}
	existing, err := h.repo.GetUserByEmail(req.Email)
	if err == nil && existing != nil {
		c.JSON(409, apiErr("该邮箱已被注册"))
		return
	}
	if err != nil && err != sql.ErrNoRows {
		c.JSON(500, apiErr("服务内部错误"))
		return
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(req.Password), bcrypt.DefaultCost)
	if err != nil {
		c.JSON(500, apiErr("密码加密失败"))
		return
	}
	user := &models.User{Email: req.Email, PasswordHash: string(hash), Nickname: req.Nickname}
	if err := h.repo.CreateUser(user); err != nil {
		c.JSON(500, apiErr("创建用户失败"))
		return
	}
	token, exp, err := middleware.GenerateToken(user.ID, user.Email, "parent", h.jwtSecret, 24*time.Hour)
	if err != nil {
		c.JSON(500, apiErr("Token生成失败"))
		return
	}
	c.JSON(201, apiData(models.LoginResponse{Token: token, ExpiresAt: exp, User: *user}))
}

func (h *Handler) Login(c *gin.Context) {
	var req models.LoginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(400, apiErr("请求参数无效"))
		return
	}
	user, err := h.repo.GetUserByEmail(req.Email)
	if err != nil {
		c.JSON(401, apiErr("邮箱或密码错误"))
		return
	}
	if bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(req.Password)) != nil {
		c.JSON(401, apiErr("邮箱或密码错误"))
		return
	}
	token, exp, _ := middleware.GenerateToken(user.ID, user.Email, "parent", h.jwtSecret, 24*time.Hour)
	c.JSON(200, apiData(models.LoginResponse{Token: token, ExpiresAt: exp, User: *user}))
}

// ===== Device =====

func (h *Handler) GetDevices(c *gin.Context) {
	userID := c.GetInt("user_id")
	devices, err := h.repo.GetDevicesByOwnerID(userID)
	if err != nil {
		c.JSON(500, apiErr("获取设备列表失败"))
		return
	}
	c.JSON(200, apiData(devices))
}

func (h *Handler) BindDevice(c *gin.Context) {
	userID := c.GetInt("user_id")
	var req models.BindDeviceRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(400, apiErr("请求参数无效"))
		return
	}

	// 生成6位随机配对码（去重）
	var code string
	for i := 0; i < 10; i++ {
		code = fmt.Sprintf("%06d", rand.Intn(1000000))
		if err := h.repo.CreatePendingBind(code, userID, req.DeviceName); err == nil {
			break
		}
	}

	c.JSON(200, apiData(gin.H{
		"pairing_code":  code,
		"device_name":   req.DeviceName,
		"valid_minutes": 30,
	}))
}

func (h *Handler) UnbindDevice(c *gin.Context) {
	userID := c.GetInt("user_id")
	id, _ := strconv.Atoi(c.Param("id"))
	if err := h.repo.UnbindDevice(id, userID); err != nil {
		c.JSON(500, apiErr("解绑失败"))
		return
	}
	c.JSON(200, apiOK("设备已解绑"))
}

// ===== Rule =====

func (h *Handler) GetRules(c *gin.Context) {
	userID := c.GetInt("user_id")
	rules, err := h.repo.GetRulesByOwnerID(userID)
	if err != nil {
		c.JSON(500, apiErr("获取规则失败"))
		return
	}

	type ruleDetail struct {
		models.Rule
		Apps      []models.RuleApp      `json:"apps"`
		Schedules []models.RuleSchedule `json:"schedules"`
		DeviceIDs []int                `json:"device_ids"`
	}
	var details []ruleDetail
	for _, r := range rules {
		apps, _ := h.repo.GetRuleApps(r.ID)
		schedules, _ := h.repo.GetRuleSchedules(r.ID)
		deviceIDs, _ := h.repo.GetRuleDevices(r.ID)
		details = append(details, ruleDetail{r, apps, schedules, deviceIDs})
	}
	c.JSON(200, apiData(details))
}

func (h *Handler) CreateRule(c *gin.Context) {
	userID := c.GetInt("user_id")
	var req models.CreateRuleRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(400, apiErr("请求参数无效"))
		return
	}
	rule := &models.Rule{Name: req.Name, OwnerID: userID, IsActive: true}
	if err := h.repo.CreateRule(rule); err != nil {
		c.JSON(500, apiErr("创建规则失败"))
		return
	}
	if len(req.AppIDs) > 0 {
		var apps []models.RuleApp
		for _, pkg := range req.AppIDs {
			apps = append(apps, models.RuleApp{RuleID: rule.ID, PackageName: pkg})
		}
		h.repo.SetRuleApps(rule.ID, apps)
	}
	if len(req.Schedules) > 0 {
		var schedules []models.RuleSchedule
		for _, s := range req.Schedules {
			schedules = append(schedules, models.RuleSchedule{RuleID: rule.ID, DaysOfWeek: s.DaysOfWeek, StartTime: s.StartTime, EndTime: s.EndTime})
		}
		h.repo.SetRuleSchedules(rule.ID, schedules)
	}
	if len(req.DeviceIDs) > 0 {
		h.repo.SetRuleDevices(rule.ID, req.DeviceIDs)
	}
	c.JSON(201, apiData(rule))
}

func (h *Handler) UpdateRule(c *gin.Context) {
	userID := c.GetInt("user_id")
	ruleID, _ := strconv.Atoi(c.Param("id"))

	rule, err := h.repo.GetRuleByID(ruleID)
	if err != nil || rule.OwnerID != userID {
		c.JSON(404, apiErr("规则不存在"))
		return
	}

	var req models.UpdateRuleRequest
	c.ShouldBindJSON(&req)

	if req.Name != nil { rule.Name = *req.Name }
	if req.IsActive != nil { rule.IsActive = *req.IsActive }
	h.repo.UpdateRule(rule)

	if req.AppIDs != nil {
		var apps []models.RuleApp
		for _, pkg := range req.AppIDs {
			apps = append(apps, models.RuleApp{RuleID: rule.ID, PackageName: pkg})
		}
		h.repo.SetRuleApps(rule.ID, apps)
	}
	if req.Schedules != nil {
		var schedules []models.RuleSchedule
		for _, s := range req.Schedules {
			schedules = append(schedules, models.RuleSchedule{RuleID: rule.ID, DaysOfWeek: s.DaysOfWeek, StartTime: s.StartTime, EndTime: s.EndTime})
		}
		h.repo.SetRuleSchedules(rule.ID, schedules)
	}
	if req.DeviceIDs != nil {
		h.repo.SetRuleDevices(rule.ID, req.DeviceIDs)
	}
	c.JSON(200, apiOK("规则已更新"))
}

func (h *Handler) DeleteRule(c *gin.Context) {
	userID := c.GetInt("user_id")
	id, _ := strconv.Atoi(c.Param("id"))
	h.repo.DeleteRule(id, userID)
	c.JSON(200, apiOK("规则已删除"))
}

func (h *Handler) ToggleRule(c *gin.Context) {
	userID := c.GetInt("user_id")
	id, _ := strconv.Atoi(c.Param("id"))
	var body struct{ IsActive bool `json:"is_active"` }
	c.ShouldBindJSON(&body)
	h.repo.ToggleRule(id, userID, body.IsActive)
	c.JSON(200, apiOK("状态已更新"))
}

// ===== Event =====

func (h *Handler) GetEvents(c *gin.Context) {
	userID := c.GetInt("user_id")

	filter := models.EventFilter{
		PackageName: c.Query("package_name"),
		StartDate:   c.Query("start_date"),
		EndDate:     c.Query("end_date"),
	}
	if v, _ := strconv.Atoi(c.Query("device_id")); v > 0 {
		filter.DeviceID = v
	}
	page := models.PaginationQuery{Page: 1, PageSize: 20}
	if v, _ := strconv.Atoi(c.Query("page")); v > 0 { page.Page = v }
	if v, _ := strconv.Atoi(c.Query("page_size")); v > 0 { page.PageSize = v }

	events, total, err := h.repo.GetEvents(userID, filter, page)
	if err != nil {
		c.JSON(500, apiErr("查询失败"))
		return
	}
	c.JSON(200, apiData(models.PaginatedResponse{
		Data: events, Total: total, Page: page.Page, PageSize: page.PageSize,
		TotalPages: (total + page.PageSize - 1) / page.PageSize,
	}))
}

func (h *Handler) GetStats(c *gin.Context) {
	userID := c.GetInt("user_id")
	stats, _ := h.repo.GetStats(userID)
	c.JSON(200, apiData(stats))
}

// ===== Child APIs =====

func (h *Handler) ChildRegister(c *gin.Context) {
	var req models.ChildRegisterRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(400, apiErr("请求参数无效"))
		return
	}

	// 查找并消费配对码
	ownerID, deviceName, err := h.repo.ConsumePendingBind(req.PairingCode)
	if err != nil {
		c.JSON(401, apiErr("配对码无效或已过期"))
		return
	}

	// 创建设备（直接绑定到家长）
	device := &models.Device{
		DeviceID:    req.DeviceID,
		DeviceName:  deviceName,
		Model:       req.Model,
		OwnerID:     &ownerID,
		PairingCode: "",
	}
	if err := h.repo.CreateDevice(device); err != nil {
		c.JSON(500, apiErr("设备创建失败"))
		return
	}

	token, exp, err := middleware.GenerateToken(device.ID, device.DeviceID, "child", h.jwtSecret, 30*24*time.Hour)
	if err != nil {
		c.JSON(500, apiErr("Token生成失败"))
		return
	}
	c.JSON(200, apiData(gin.H{"token": token, "expires_at": exp, "device_id": device.DeviceID}))
}

func (h *Handler) ChildGetConfig(c *gin.Context) {
	deviceID := c.GetInt("user_id")
	config, err := h.repo.GetChildConfig(deviceID)
	if err != nil {
		c.JSON(500, apiErr("获取配置失败"))
		return
	}
	c.JSON(200, apiData(config))
}

func (h *Handler) ChildReportApps(c *gin.Context) {
	deviceID := c.GetInt("user_id")
	var req models.ChildAppsRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(400, apiErr("请求参数无效"))
		return
	}
	h.repo.UpsertChildApps(deviceID, req.Apps)
	c.JSON(200, apiOK("同步完成"))
}

func (h *Handler) ChildReportEvents(c *gin.Context) {
	deviceID := c.GetInt("user_id")
	var req models.EventReportRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(400, apiErr("请求参数无效"))
		return
	}
	var events []models.Event
	for _, e := range req.Events {
		t, _ := time.Parse(time.RFC3339, e.BlockedAt)
		events = append(events, models.Event{DeviceID: deviceID, PackageName: e.PackageName, AppName: e.AppName, BlockedAt: t})
	}
	h.repo.CreateEvents(events)
	c.JSON(200, apiOK("上报完成"))
}

func (h *Handler) ChildHeartbeat(c *gin.Context) {
	deviceID := c.GetInt("user_id")
	device, err := h.repo.GetDeviceByID(deviceID)
	if err != nil {
		c.JSON(404, apiErr("设备不存在"))
		return
	}
	h.repo.UpdateDeviceOnline(device.DeviceID, true)
	c.JSON(200, apiOK("ok"))
}

// ===== Device Apps =====

func (h *Handler) GetDeviceApps(c *gin.Context) {
	userID := c.GetInt("user_id")
	deviceID, _ := strconv.Atoi(c.Param("id"))

	device, err := h.repo.GetDeviceByID(deviceID)
	if err != nil || device.OwnerID == nil || *device.OwnerID != userID {
		c.JSON(404, apiErr("设备不存在"))
		return
	}

	search := c.Query("search")
	page := models.PaginationQuery{Page: 1, PageSize: 50}
	if v, _ := strconv.Atoi(c.Query("page")); v > 0 { page.Page = v }

	apps, total, err := h.repo.GetChildApps(deviceID, search, page)
	if err != nil {
		c.JSON(500, apiErr("获取应用列表失败"))
		return
	}
	c.JSON(200, apiData(models.PaginatedResponse{
		Data: apps, Total: total, Page: page.Page, PageSize: page.PageSize,
		TotalPages: (total + page.PageSize - 1) / page.PageSize,
	}))
}

// ===== Log =====

func (h *Handler) UploadLogs(c *gin.Context) {
	var req models.LogUploadRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(400, apiErr("日志格式无效"))
		return
	}

	var logs []models.AppLog
	for _, l := range req.Logs {
		t, _ := time.Parse(time.RFC3339, l.Timestamp)
		log := models.AppLog{
			Source: req.Source, Level: l.Level, Tag: l.Tag,
			Message: l.Message, Stacktrace: l.Stacktrace, LoggedAt: t,
		}
		if req.Source == "child-app" {
			log.DeviceID = c.GetString("device_id")
		} else {
			v := c.GetInt("user_id")
			log.OwnerID = &v
		}
		logs = append(logs, log)
	}

	if err := h.repo.InsertLogs(logs); err != nil {
		c.JSON(500, apiErr("日志写入失败"))
		return
	}
	c.JSON(200, gin.H{"success": true, "accepted": len(logs)})
}

func (h *Handler) QueryLogs(c *gin.Context) {
	filter := models.LogFilter{
		Source: c.Query("source"),
		Level:  c.Query("level"),
		Start:  c.Query("start"),
		End:    c.Query("end"),
	}
	page := models.PaginationQuery{Page: 1, PageSize: 50}
	if v, _ := strconv.Atoi(c.Query("page")); v > 0 { page.Page = v }
	if v, _ := strconv.Atoi(c.Query("page_size")); v > 0 { page.PageSize = v }

	logs, total, err := h.repo.QueryLogs(filter, page)
	if err != nil {
		c.JSON(500, apiErr("查询日志失败"))
		return
	}
	c.JSON(200, apiData(models.PaginatedResponse{
		Data: logs, Total: total, Page: page.Page, PageSize: page.PageSize,
		TotalPages: (total + page.PageSize - 1) / page.PageSize,
	}))
}

func (h *Handler) ExportLogs(c *gin.Context) {
	filter := models.LogFilter{
		Source: c.Query("source"),
		Level:  c.Query("level"),
		Start:  c.Query("start"),
		End:    c.Query("end"),
	}
	page := models.PaginationQuery{Page: 1, PageSize: 10000}
	logs, _, _ := h.repo.QueryLogs(filter, page)

	c.Header("Content-Type", "application/x-ndjson")
	c.Header("Content-Disposition", "attachment; filename=app_logs_export.jsonl")
	for _, l := range logs {
		c.JSON(200, l)
		c.Writer.Write([]byte("\n"))
	}
}

// ===== Health =====

func (h *Handler) Health(c *gin.Context) {
	c.JSON(200, gin.H{"status": "ok"})
}

// ===== Helpers =====

func apiData(data interface{}) gin.H {
	return gin.H{"success": true, "data": data}
}

func apiOK(msg string) gin.H {
	return gin.H{"success": true, "message": msg}
}

func apiErr(msg string) gin.H {
	return gin.H{"success": false, "message": msg}
}
