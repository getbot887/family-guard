package models

import "time"

// ===== 数据表模型 =====

type User struct {
	ID           int       `json:"id" db:"id"`
	Email        string    `json:"email" db:"email"`
	PasswordHash string    `json:"-" db:"password_hash"`
	Nickname     string    `json:"nickname" db:"nickname"`
	CreatedAt    time.Time `json:"created_at" db:"created_at"`
}

type Device struct {
	ID          int        `json:"id" db:"id"`
	DeviceID    string     `json:"device_id" db:"device_id"`
	DeviceName  string     `json:"device_name" db:"device_name"`
	Model       string     `json:"model" db:"model"`
	OwnerID     *int       `json:"owner_id" db:"owner_id"`
	PairingCode string     `json:"pairing_code,omitempty" db:"pairing_code"`
	IsOnline    bool       `json:"is_online" db:"is_online"`
	LastSeenAt  *time.Time `json:"last_seen_at" db:"last_seen_at"`
	CreatedAt   time.Time  `json:"created_at" db:"created_at"`
}

type Rule struct {
	ID        int       `json:"id" db:"id"`
	Name      string    `json:"name" db:"name"`
	OwnerID   int       `json:"owner_id" db:"owner_id"`
	IsActive  bool      `json:"is_active" db:"is_active"`
	CreatedAt time.Time `json:"created_at" db:"created_at"`
	UpdatedAt time.Time `json:"updated_at" db:"updated_at"`
}

type RuleApp struct {
	ID          int    `json:"id" db:"id"`
	RuleID      int    `json:"rule_id" db:"rule_id"`
	PackageName string `json:"package_name" db:"package_name"`
	AppName     string `json:"app_name" db:"app_name"`
}

type RuleSchedule struct {
	ID         int    `json:"id" db:"id"`
	RuleID     int    `json:"rule_id" db:"rule_id"`
	DaysOfWeek []int  `json:"days_of_week" db:"days_of_week"`
	StartTime  string `json:"start_time" db:"start_time"`
	EndTime    string `json:"end_time" db:"end_time"`
}

type RuleDevice struct {
	RuleID   int `json:"rule_id" db:"rule_id"`
	DeviceID int `json:"device_id" db:"device_id"`
}

type Event struct {
	ID          int       `json:"id" db:"id"`
	DeviceID    int       `json:"device_id" db:"device_id"`
	PackageName string    `json:"package_name" db:"package_name"`
	AppName     string    `json:"app_name" db:"app_name"`
	BlockedAt   time.Time `json:"blocked_at" db:"blocked_at"`
}

type ChildApp struct {
	ID          int       `json:"id" db:"id"`
	DeviceID    int       `json:"device_id" db:"device_id"`
	PackageName string    `json:"package_name" db:"package_name"`
	AppName     string    `json:"app_name" db:"app_name"`
	SyncedAt    time.Time `json:"synced_at" db:"synced_at"`
}

type AppLog struct {
	ID         int64     `json:"id" db:"id"`
	Source     string    `json:"source" db:"source"`
	DeviceID   string    `json:"device_id,omitempty" db:"device_id"`
	OwnerID    *int      `json:"owner_id,omitempty" db:"owner_id"`
	Level      string    `json:"level" db:"level"`
	Tag        string    `json:"tag" db:"tag"`
	Message    string    `json:"message" db:"message"`
	Stacktrace string    `json:"stacktrace,omitempty" db:"stacktrace"`
	LoggedAt   time.Time `json:"logged_at" db:"logged_at"`
	UploadedAt time.Time `json:"uploaded_at" db:"uploaded_at"`
}

// ===== 请求/响应结构体 =====

type RegisterRequest struct {
	Email    string `json:"email" binding:"required,email"`
	Password string `json:"password" binding:"required,min=6"`
	Nickname string `json:"nickname"`
}

type LoginRequest struct {
	Email    string `json:"email" binding:"required,email"`
	Password string `json:"password" binding:"required"`
}

type LoginResponse struct {
	Token     string `json:"token"`
	ExpiresAt int64  `json:"expires_at"`
	User      User   `json:"user"`
}

type BindDeviceRequest struct {
	DeviceName  string `json:"device_name" binding:"required"`
}

type CreateRuleRequest struct {
	Name      string            `json:"name" binding:"required"`
	AppIDs    []string          `json:"app_ids"`
	Schedules []ScheduleInput   `json:"schedules"`
	DeviceIDs []int             `json:"device_ids"`
}

type UpdateRuleRequest struct {
	Name      *string           `json:"name"`
	IsActive  *bool             `json:"is_active"`
	AppIDs    []string          `json:"app_ids"`
	Schedules []ScheduleInput   `json:"schedules"`
	DeviceIDs []int             `json:"device_ids"`
}

type ScheduleInput struct {
	DaysOfWeek []int  `json:"days_of_week"`
	StartTime  string `json:"start_time"`
	EndTime    string `json:"end_time"`
}

type ChildRegisterRequest struct {
	DeviceID    string `json:"device_id" binding:"required"`
	DeviceName  string `json:"device_name"`
	Model       string `json:"model"`
	PairingCode string `json:"pairing_code" binding:"required"`
}

type ChildAppsRequest struct {
	Apps []ChildAppItem `json:"apps" binding:"required"`
}

type ChildAppItem struct {
	PackageName string `json:"package_name" binding:"required"`
	AppName     string `json:"app_name"`
}

type EventReportRequest struct {
	Events []EventItem `json:"events" binding:"required"`
}

type EventItem struct {
	PackageName string `json:"package_name" binding:"required"`
	AppName     string `json:"app_name"`
	BlockedAt   string `json:"blocked_at" binding:"required"`
}

type LogUploadRequest struct {
	Source string    `json:"source" binding:"required"` // parent-app | child-app
	Logs   []LogItem `json:"logs" binding:"required"`
}

type LogItem struct {
	Level      string `json:"level" binding:"required"`
	Tag        string `json:"tag" binding:"required"`
	Message    string `json:"message" binding:"required"`
	Stacktrace string `json:"stacktrace,omitempty"`
	Timestamp  string `json:"timestamp" binding:"required"`
}

type PaginationQuery struct {
	Page     int `form:"page,default=1"`
	PageSize int `form:"page_size,default=20"`
}

type PaginatedResponse struct {
	Data       interface{} `json:"data"`
	Total      int         `json:"total"`
	Page       int         `json:"page"`
	PageSize   int         `json:"page_size"`
	TotalPages int         `json:"total_pages"`
}

type EventFilter struct {
	DeviceID    int    `form:"device_id"`
	PackageName string `form:"package_name"`
	StartDate   string `form:"start_date"`
	EndDate     string `form:"end_date"`
}

type LogFilter struct {
	Source string `form:"source"`
	Level  string `form:"level"`
	Start  string `form:"start"`
	End    string `form:"end"`
}

type StatsResponse struct {
	TotalBlocked int        `json:"total_blocked"`
	TodayBlocked int        `json:"today_blocked"`
	TopApps      []AppStat  `json:"top_apps"`
	DailyStats   []DayStat  `json:"daily_stats"`
}

type AppStat struct {
	PackageName string `json:"package_name"`
	AppName     string `json:"app_name"`
	Count       int    `json:"count"`
}

type DayStat struct {
	Date  string `json:"date"`
	Count int    `json:"count"`
}

type APIResponse struct {
	Success bool        `json:"success"`
	Message string      `json:"message,omitempty"`
	Data    interface{} `json:"data,omitempty"`
}
