package middleware

import (
	"time"

	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
)

type Claims struct {
	UserID   int    `json:"user_id"`
	Email    string `json:"email"`
	UserType string `json:"user_type"` // "parent" | "child"
	jwt.RegisteredClaims
}

func GenerateToken(userID int, email, userType, secret string, expiry time.Duration) (string, int64, error) {
	exp := time.Now().Add(expiry)
	claims := Claims{
		userID, email, userType,
		jwt.RegisteredClaims{ExpiresAt: jwt.NewNumericDate(exp), IssuedAt: jwt.NewNumericDate(time.Now()), Issuer: "family-guard"},
	}
	token, err := jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString([]byte(secret))
	return token, exp.Unix(), err
}

func ParseToken(tokenStr, secret string) (*Claims, error) {
	token, err := jwt.ParseWithClaims(tokenStr, &Claims{}, func(t *jwt.Token) (interface{}, error) {
		return []byte(secret), nil
	})
	if err != nil {
		return nil, err
	}
	claims, ok := token.Claims.(*Claims)
	if !ok || !token.Valid {
		return nil, jwt.ErrSignatureInvalid
	}
	return claims, nil
}

func ParentAuth(secret string) gin.HandlerFunc {
	return func(c *gin.Context) {
		claims := extractClaims(c, secret)
		if claims == nil || claims.UserType != "parent" {
			c.AbortWithStatusJSON(401, gin.H{"success": false, "message": "未授权"})
			return
		}
		c.Set("user_id", claims.UserID)
		c.Set("email", claims.Email)
		c.Next()
	}
}

func ChildAuth(secret string) gin.HandlerFunc {
	return func(c *gin.Context) {
		tokenStr := c.GetHeader("X-Device-Token")
		claims := parseTokenString(tokenStr, secret)
		if claims == nil {
			c.AbortWithStatusJSON(401, gin.H{"success": false, "message": "设备token无效"})
			return
		}
		c.Set("user_id", claims.UserID)
		c.Set("device_id", claims.Email)
		c.Next()
	}
}

func extractClaims(c *gin.Context, secret string) *Claims {
	tokenStr := c.GetHeader("Authorization")
	if len(tokenStr) > 7 && tokenStr[:7] == "Bearer " {
		return parseTokenString(tokenStr[7:], secret)
	}
	return nil
}

func parseTokenString(tokenStr, secret string) *Claims {
	if tokenStr == "" {
		return nil
	}
	claims, err := ParseToken(tokenStr, secret)
	if err != nil {
		return nil
	}
	return claims
}
