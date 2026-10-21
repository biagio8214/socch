#ifndef PULSE_ROOM_HUD_RENDERER_H
#define PULSE_ROOM_HUD_RENDERER_H

#include <GLES3/gl3.h>
#include <chrono>
#include <string>
#include <vector>

struct GameActivity;

class HudRenderer {
public:
    struct Color { float r; float g; float b; float a; };

    HudRenderer() = default;
    ~HudRenderer();
    bool initialize(const char *internalDataPath, GameActivity *activity);
    void render(int width, int height);
    void handleTouch(int action, float x, float y, int width, int height);
    void release();

private:
    struct Vertex { float x; float y; float r; float g; float b; float a; };
    void drawRect(float x, float y, float width, float height, Color color);
    void drawSegment(float x1, float y1, float x2, float y2, float thickness, Color color);
    void drawText(const char *text, float x, float y, float cell, Color color);
    void drawToolIcon(int tool, float centerX, float centerY, float size, Color color);
    void drawFrame(float width, float height, float elapsedSeconds);
    const char *requestLabel(int requestIndex) const;
    const char *requestLabelRecord(int requestIndex) const;
    void selectTool(int tool, float elapsedSeconds);
    void deliverCurrentRequest(float elapsedSeconds, bool anticipated);
    const char *toolName(int tool) const;
    const char *careerRank() const;
    void finishTurn(bool completed);

    GLuint program_ = 0;
    GLuint vertexArray_ = 0;
    GLuint vertexBuffer_ = 0;
    GLint viewportLocation_ = -1;
    std::chrono::steady_clock::time_point startTime_;
    std::vector<Vertex> vertices_;
    int selectedTool_ = 0;
    int precision_ = 100;
    int steadyMoves_ = 0;
    int requestIndex_ = 0;
    int queuedTool_ = -1;
    int score_ = 0;
    int mistakes_ = 0;
    int anticipations_ = 0;
    int penaltySeconds_ = 0;
    int bonusSeconds_ = 0;
    int gauzesRemaining_ = 12;
    int gauzesUsed_ = 0;
    int suturePacks_ = 8;
    int prolenePacks_ = 4;
    int prolene40Packs_ = 1;
    int rumelTourniquets_ = 3;
    int silkTies_ = 6;
    int ethbGreenPacks_ = 4;
    int ethbWhitePacks_ = 4;
    int sternalWires_ = 6;
    int vicrylZeroPacks_ = 2;
    int vicrylTwoZeroPacks_ = 2;
    int monocrylThreeZeroPacks_ = 2;
    int gauzeCountGuess_ = 0;
    int careerPoints_ = 0;
    int pendingPlacementTool_ = -1;
    float requestStartedAt_ = 0.f;
    float feedbackUntil_ = 0.f;
    bool countingGauze_ = false;
    bool heartExposed_ = false;
    bool sterileDrapePlaced_ = false;
    bool aorticCannulaPlaced_ = false;
    bool cavalCannulaPlaced_ = false;
    bool blueRecoveryConnected_ = false;
    bool yellowVentConnected_ = false;
    bool aorticCardioplegiaPursePlaced_ = false;
    bool superiorCavalPursePlaced_ = false;
    bool inferiorCavalPursePlaced_ = false;
    bool aorticClampApplied_ = false;
    bool cardioplegiaLineConnected_ = false;
    bool coolingPlaced_ = false;
    bool mitralRetractorPlaced_ = false;
    bool sumpVentPlaced_ = false;
    bool rootVentPlaced_ = false;
    bool aorticClampReleased_ = false;
    bool cavalCannulaRemoved_ = false;
    bool aorticCannulaRemoved_ = false;
    bool preClosureCountComplete_ = false;
    bool turnFinished_ = false;
    std::string feedback_;
    std::string careerDataFile_;
    std::string lastScoreFile_;
    GameActivity *activity_ = nullptr;
    bool touchActive_ = false;
    float touchX_ = 0.f;
    float touchY_ = 0.f;
};

#endif