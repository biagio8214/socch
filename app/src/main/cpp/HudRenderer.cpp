#include "HudRenderer.h"

#include <android/input.h>
#include <android/log.h>
#include <game-activity/GameActivity.h>
#include <algorithm>
#include <array>
#include <cmath>
#include <cstddef>
#include <cstdio>
#include <cstring>
#include <fstream>

namespace {
constexpr char kVertexShader[] = R"shader(#version 300 es
layout(location=0) in vec2 aPosition;
layout(location=1) in vec4 aColor;
uniform vec2 uViewport;
out vec4 vertexColor;
void main() {
    vec2 clip = vec2(aPosition.x / uViewport.x * 2.0 - 1.0, 1.0 - aPosition.y / uViewport.y * 2.0);
    gl_Position = vec4(clip, 0.0, 1.0);
    vertexColor = aColor;
}
)shader";

constexpr char kFragmentShader[] = R"shader(#version 300 es
precision mediump float;
in vec4 vertexColor;
out vec4 outColor;
void main() { outColor = vertexColor; }
)shader";

GLuint compileShader(GLenum type, const char *source) {
    const GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    GLint compiled = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (compiled == GL_FALSE) {
        char message[512]{};
        glGetShaderInfoLog(shader, sizeof(message), nullptr, message);
        __android_log_print(ANDROID_LOG_ERROR, "PulseRoomHud", "%s", message);
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

std::array<unsigned char, 7> glyph(char c) {
    switch (c) {
        case 'A': return {14,17,17,31,17,17,17}; case 'B': return {30,17,17,30,17,17,30};
        case 'C': return {14,17,16,16,16,17,14}; case 'D': return {30,17,17,17,17,17,30};
        case 'E': return {31,16,16,30,16,16,31}; case 'F': return {31,16,16,30,16,16,16};
        case 'G': return {14,17,16,23,17,17,15}; case 'H': return {17,17,17,31,17,17,17};
        case 'I': return {14,4,4,4,4,4,14}; case 'J': return {7,2,2,2,18,18,12};
        case 'K': return {17,18,20,24,20,18,17}; case 'L': return {16,16,16,16,16,16,31};
        case 'M': return {17,27,21,21,17,17,17}; case 'N': return {17,25,25,21,19,19,17};
        case 'O': return {14,17,17,17,17,17,14}; case 'P': return {30,17,17,30,16,16,16};
        case 'Q': return {14,17,17,17,21,18,13}; case 'R': return {30,17,17,30,20,18,17};
        case 'S': return {15,16,16,14,1,1,30}; case 'T': return {31,4,4,4,4,4,4};
        case 'U': return {17,17,17,17,17,17,14}; case 'V': return {17,17,17,17,17,10,4};
        case 'W': return {17,17,17,21,21,21,10}; case 'X': return {17,17,10,4,10,17,17};
        case 'Y': return {17,17,10,4,4,4,4}; case 'Z': return {31,1,2,4,8,16,31};
        case '0': return {14,17,19,21,25,17,14}; case '1': return {4,12,4,4,4,4,14};
        case '2': return {14,17,1,2,4,8,31}; case '3': return {30,1,1,14,1,1,30};
        case '4': return {2,6,10,18,31,2,2}; case '5': return {31,16,16,30,1,1,30};
        case '6': return {14,16,16,30,17,17,14}; case '7': return {31,1,2,4,8,8,8};
        case '8': return {14,17,17,14,17,17,14}; case '9': return {14,17,17,15,1,1,14};
        case ':': return {0,4,4,0,4,4,0}; case '/': return {1,2,2,4,8,8,16};
        case '-': return {0,0,0,31,0,0,0}; case '+': return {0,4,4,31,4,4,0};
        case '%': return {17,2,4,8,16,17,0}; case '.': return {0,0,0,0,0,6,6};
        default: return {0,0,0,0,0,0,0};
    }
}

constexpr HudRenderer::Color kWhite{0.91f,0.96f,0.93f,1.f};
constexpr HudRenderer::Color kMuted{0.57f,0.72f,0.67f,1.f};
constexpr HudRenderer::Color kGreen{0.27f,0.95f,0.67f,1.f};
constexpr HudRenderer::Color kAmber{1.f,0.73f,0.22f,1.f};
constexpr HudRenderer::Color kRed{1.f,0.29f,0.25f,1.f};
constexpr HudRenderer::Color kBlue{0.26f,0.65f,1.f,1.f};
constexpr HudRenderer::Color kYellow{1.f,0.81f,0.24f,1.f};
constexpr HudRenderer::Color kPanel{0.025f,0.09f,0.085f,0.91f};
constexpr int kVisibleToolSlots = 5;

enum class PlacementAction {
    None, SterileDrape, AorticPurse, AorticCardioplegiaPurse, SuperiorCavalPurse, InferiorCavalPurse,
    AorticCannula, CavalCannula, ConnectBlueRecovery, ConnectYellowVent, ApplyAorticClamp,
    ConnectCardioplegia, PlaceIce, MitralRetractor, SumpVent, RootVent, ReleaseAorticClamp,
    DecannulateCaval, DecannulateAorta, StartFinalCount
};

enum class StockItem {
    None, Gauze, Suture, Prolene3, Prolene4, Rumel, Silk, EthibondGreen, EthibondWhite,
    SternalWire, VicrylZero, VicrylTwoZero, MonocrylThreeZero
};

struct RequestStep {
    int tool;
    const char *label;
    PlacementAction placement;
    StockItem stock;
    int stockCount;
};

constexpr RequestStep request(int tool, const char *label, PlacementAction placement = PlacementAction::None,
        StockItem stock = StockItem::None, int stockCount = 0) {
    return {tool, label, placement, stock, stockCount};
}

constexpr const char *kToolNames[]{
    "PINZA ADSON", "BISTURI LAMA 21", "BISTURI ELETTRICO", "GARZA", "FILO SUTURA",
    "CERA OSSA", "SEGA STERNALE", "POMPA CEC", "PORTA AGHI", "TI-CRON 1", "DIVARICATORE TORACICO",
    "TELINO STERILE", "PORTA AGHI LUNGO", "PINZA DEBAKEY", "PROLENE 3-0", "FORBICI DA FILO",
    "LACCI RUMEL TOURNEQUET", "CANNULA AORTICA", "CANNULA CAVA", "TUBO BLU RECUPERO", "TUBO GIALLO SUMP VENT",
    "PROLENE 4-0", "LACCIO TI-CRON", "BISTURI 11", "FORBICI POTTS", "CLAMP CANNULA", "SETA 1",
    "CLAMP AORTICO", "LINEA CARDIOPLEGIA", "GHIACCIO STERILE", "BISTURI 11 LUNGO", "POTTS LUNGHE",
    "DEBAKEY LUNGA", "DIVARICATORE MITRALE", "SUMP VENT", "SETA 2-0", "UNCINO VALVOLARE",
    "FORBICI MITRALE", "PINZA ALLIS", "SIZER PROTESI", "RASTRELLIERA SUTURE", "ETHIBOND VERDE 2-0",
    "ETHIBOND BIANCO 2-0", "SUPPORTO PROTESI", "SPINGINODO", "ROOT VENT", "PROLENE 4-0 FELTRINO",
    "DRENAGGIO TORACICO", "KELLY CURVA", "FILI ACCIAIO 5/6", "TWISTER", "TRANCINO FILI",
    "VICRYL 0", "VICRYL 2-0", "MONOCRYL 3-0"};

constexpr const char *kToolLabels[]{
    "ADSON", "LAMA 21", "ELETTR.", "GARZA", "FILO", "CERA", "SEGA", "POMPA", "PORTA", "TICRON", "DIVAR.",
    "TELINO", "P LUNGO", "DEBAKEY", "PROL 3-0", "FORBICI", "RUMEL", "AORTA", "CAVA", "TUBO BLU", "SUMP VENT",
    "PROL 4-0", "TI-CRON", "BIST 11", "POTTS", "CLAMP", "SETA 1", "CLAMP A", "CARDIO", "GHIACCIO",
    "11 LUNGO", "POTTS L", "DEBAKEY L", "MITRALE", "SUMP", "SETA 2-0", "UNCINO", "FORB MIT", "ALLIS",
    "SIZER", "RASTRO", "ETH VERDE", "ETH BIANCO", "PROTESI", "PUSHER", "ROOT VENT", "PROLENE 4-0",
    "DRENAGGIO", "KELLY", "FILI 5/6", "TWISTER", "TRANCINO", "VICRYL 0", "VICRYL 2-0", "MONOCRYL"};

constexpr int kToolCount = sizeof(kToolNames) / sizeof(kToolNames[0]);
constexpr RequestStep kRequestSequence[]{
    request(3, "PASSA GARZA", PlacementAction::None, StockItem::Gauze, 1),
    request(1, "BISTURI LAMA 21"), request(2, "BISTURI ELETTRICO"), request(5, "CERA D'OSSO"),
    request(6, "SEGA STERNALE"), request(10, "DIVARICATORE TORACICO"),
    request(8, "PUNTO CUTE 1/2", PlacementAction::None, StockItem::Suture, 1),
    request(8, "PUNTO CUTE 2/2", PlacementAction::None, StockItem::Suture, 1),
    request(9, "TI-CRON 1 SOSPENSIONE 1/2", PlacementAction::None, StockItem::Suture, 1),
    request(9, "TI-CRON 1 SOSPENSIONE 2/2", PlacementAction::None, StockItem::Suture, 1),
    request(11, "TELINO SOTTO INCISIONE", PlacementAction::SterileDrape),
    request(12, "PORTA AGHI LUNGO"), request(13, "PINZA DEBAKEY"), request(14, "PREPARA PROLENE 3-0"),
    request(14, "BORSA AORTICA ESTERNA 1/2", PlacementAction::AorticPurse, StockItem::Prolene3, 1),
    request(14, "BORSA AORTICA INTERNA 2/2", PlacementAction::AorticPurse, StockItem::Prolene3, 1),
    request(8, "PORTAGHI CARDIOPLEGIA"), request(13, "DEBAKEY RADICE AORTICA"),
    request(16, "TOURNEQUET CARDIOPLEGIA", PlacementAction::None, StockItem::Rumel, 1),
    request(21, "BORSA CARDIOPLEGIA PROLENE 4-0", PlacementAction::AorticCardioplegiaPurse, StockItem::Prolene4, 1),
    request(15, "FORBICI DA FILO"), request(12, "PORTA AGHI PER CAVALI"), request(13, "DEBAKEY PER CAVALI"),
    request(16, "TOURNEQUET VCS VCI X2", PlacementAction::None, StockItem::Rumel, 2),
    request(14, "BORSA VCS PROLENE 3-0", PlacementAction::SuperiorCavalPurse, StockItem::Prolene3, 1),
    request(14, "BORSA VCI PROLENE 3-0", PlacementAction::InferiorCavalPurse, StockItem::Prolene3, 1),
    request(22, "LACCIO DI SICUREZZA TI-CRON", PlacementAction::None, StockItem::Suture, 1),
    request(23, "BISTURI 11 AORTA"), request(24, "FORBICI POTTS"), request(13, "PINZA DEBAKEY"),
    request(17, "POSIZIONA CANNULA AORTICA", PlacementAction::AorticCannula),
    request(25, "CLAMP PER CANNULA AORTICA"), request(26, "SETA 1 FISSAGGIO AORTA", PlacementAction::None, StockItem::Silk, 1),
    request(23, "BISTURI 11 CAVA"), request(24, "FORBICI POTTS"),
    request(18, "POSIZIONA CANNULA CAVA", PlacementAction::CavalCannula),
    request(26, "SETA 1 FISSAGGIO CAVA", PlacementAction::None, StockItem::Silk, 1),
    request(19, "TUBO BLU ASPIRATORE RECUPERO", PlacementAction::ConnectBlueRecovery),
    request(20, "TUBO GIALLO SUMP VENT", PlacementAction::ConnectYellowVent),
    request(27, "CLAMPAGGIO AORTA", PlacementAction::ApplyAorticClamp),
    request(28, "COLLEGA LINEA CARDIOPLEGIA", PlacementAction::ConnectCardioplegia),
    request(29, "GHIACCIO STERILE NEL PERICARDIO", PlacementAction::PlaceIce),
    request(30, "BISTURI 11 LUNGO SOLCO ATRIALE"), request(31, "FORBICI POTTS LUNGHE"),
    request(32, "DEBAKEY LUNGA"), request(33, "DIVARICATORE MITRALE", PlacementAction::MitralRetractor),
    request(34, "POSIZIONA SUMP VENT", PlacementAction::SumpVent),
    request(35, "SETA 2-0 TRAZIONE ATRIALE", PlacementAction::None, StockItem::Silk, 1),
    request(36, "UNCINO VALVOLARE"), request(30, "BISTURI 11 LUNGO"),
    request(37, "FORBICI MITRALE LUNGHE"), request(38, "PINZA ALLIS"), request(39, "SIZER PROTESI"),
    request(12, "PORTA AGHI LUNGO"), request(32, "DEBAKEY LUNGA"), request(40, "RASTRELLIERA SUTURE"),
    request(41, "ANELLO ETHIBOND VERDE 1/4", PlacementAction::None, StockItem::EthibondGreen, 1),
    request(42, "ANELLO ETHIBOND BIANCO 2/4", PlacementAction::None, StockItem::EthibondWhite, 1),
    request(41, "ANELLO ETHIBOND VERDE 3/4", PlacementAction::None, StockItem::EthibondGreen, 1),
    request(42, "ANELLO ETHIBOND BIANCO 4/4", PlacementAction::None, StockItem::EthibondWhite, 1),
    request(41, "ANELLO ETHIBOND VERDE 5/8", PlacementAction::None, StockItem::EthibondGreen, 1),
    request(42, "ANELLO ETHIBOND BIANCO 6/8", PlacementAction::None, StockItem::EthibondWhite, 1),
    request(41, "ANELLO ETHIBOND VERDE 7/8", PlacementAction::None, StockItem::EthibondGreen, 1),
    request(42, "ANELLO ETHIBOND BIANCO 8/8", PlacementAction::None, StockItem::EthibondWhite, 1),
    request(43, "SUPPORTO PROTESI"), request(8, "PORTA AGHI CORTO"),
    request(41, "PASSA PUNTI VERDI SULLA PROTESI"), request(42, "PASSA PUNTI BIANCHI SULLA PROTESI"),
    request(43, "DISCESA PROTESI SUL SIZER"), request(44, "SPINGINODO"), request(15, "FORBICI DA FILO"),
    request(44, "ANNODA E TAGLIA PUNTI"),
    request(14, "CHIUSURA ATRIO PROLENE 3-0", PlacementAction::None, StockItem::Prolene3, 1),
    request(45, "POSIZIONA ROOT VENT", PlacementAction::RootVent),
    request(27, "DECLAMPAGGIO AORTA", PlacementAction::ReleaseAorticClamp),
    request(18, "DECANNULA CAVA", PlacementAction::DecannulateCaval),
    request(17, "DECANNULA AORTA", PlacementAction::DecannulateAorta),
    request(25, "PINZA DA SERRARE"), request(15, "FORBICI"),
    request(46, "RINFORZO PROLENE 4-0", PlacementAction::None, StockItem::Prolene4, 1),
    request(2, "CONTROLLO ELETTROBISTURI"),
    request(3, "CONTEGGIO GARZE FERRI E AGHI", PlacementAction::StartFinalCount),
    request(47, "DRENAGGIO TORACICO"), request(48, "KELLY CURVA"),
    request(26, "SETA 0/1 FISSA DRENAGGIO", PlacementAction::None, StockItem::Silk, 1),
    request(49, "FILI ACCIAIO STERNALI 5/6", PlacementAction::None, StockItem::SternalWire, 6),
    request(50, "TWISTER STRINGIFILI"), request(51, "TRANCINO FILI ACCIAIO"),
    request(52, "CHIUSURA MUSCOLARE VICRYL 0", PlacementAction::None, StockItem::VicrylZero, 1),
    request(53, "CHIUSURA SOTTOCUTE VICRYL 2-0", PlacementAction::None, StockItem::VicrylTwoZero, 1),
    request(54, "CHIUSURA CUTE MONOCRYL 3-0", PlacementAction::None, StockItem::MonocrylThreeZero, 1)
};
constexpr int kRequestCount = sizeof(kRequestSequence) / sizeof(kRequestSequence[0]);
}

HudRenderer::~HudRenderer() { release(); }

bool HudRenderer::initialize(const char *internalDataPath, GameActivity *activity) {
    const GLuint vertexShader = compileShader(GL_VERTEX_SHADER, kVertexShader);
    const GLuint fragmentShader = compileShader(GL_FRAGMENT_SHADER, kFragmentShader);
    if (!vertexShader || !fragmentShader) {
        if (vertexShader) glDeleteShader(vertexShader);
        if (fragmentShader) glDeleteShader(fragmentShader);
        return false;
    }
    program_ = glCreateProgram();
    glAttachShader(program_, vertexShader);
    glAttachShader(program_, fragmentShader);
    glLinkProgram(program_);
    glDeleteShader(vertexShader);
    glDeleteShader(fragmentShader);
    GLint linked = GL_FALSE;
    glGetProgramiv(program_, GL_LINK_STATUS, &linked);
    if (linked == GL_FALSE) {
        char message[512]{};
        glGetProgramInfoLog(program_, sizeof(message), nullptr, message);
        __android_log_print(ANDROID_LOG_ERROR, "PulseRoomHud", "%s", message);
        release();
        return false;
    }
    viewportLocation_ = glGetUniformLocation(program_, "uViewport");
    glGenVertexArrays(1, &vertexArray_);
    glGenBuffers(1, &vertexBuffer_);
    glBindVertexArray(vertexArray_);
    glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer_);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, sizeof(Vertex), reinterpret_cast<void *>(offsetof(Vertex, x)));
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 4, GL_FLOAT, GL_FALSE, sizeof(Vertex), reinterpret_cast<void *>(offsetof(Vertex, r)));
    glBindVertexArray(0);
    startTime_ = std::chrono::steady_clock::now();
    activity_ = activity;
    if (internalDataPath && *internalDataPath) {
        careerDataFile_ = std::string(internalDataPath) + "/instrumentist_career.dat";
        lastScoreFile_ = std::string(internalDataPath) + "/last_turn_score.dat";
        std::ifstream savedCareer(careerDataFile_);
        savedCareer >> careerPoints_;
    }
    vertices_.reserve(12000);
    return true;
}

void HudRenderer::release() {
    if (vertexBuffer_) glDeleteBuffers(1, &vertexBuffer_);
    if (vertexArray_) glDeleteVertexArrays(1, &vertexArray_);
    if (program_) glDeleteProgram(program_);
    vertexBuffer_ = vertexArray_ = program_ = 0;
}

void HudRenderer::render(int width, int height) {
    if (!program_ || width <= 0 || height <= 0) return;
    const float scale = std::min(width / 420.f, height / 780.f);
    const float virtualWidth = width / scale, virtualHeight = height / scale;
    const float elapsed = std::chrono::duration<float>(std::chrono::steady_clock::now() - startTime_).count();
    vertices_.clear();
    drawFrame(virtualWidth, virtualHeight, elapsed);
    if (touchActive_) {
        drawSegment(touchX_ / scale - 18.f, touchY_ / scale, touchX_ / scale + 18.f, touchY_ / scale, 2.f, kGreen);
        drawSegment(touchX_ / scale, touchY_ / scale - 18.f, touchX_ / scale, touchY_ / scale + 18.f, 2.f, kGreen);
        drawToolIcon(selectedTool_, touchX_ / scale, touchY_ / scale - 23.f, 13.f, kWhite);
    }
    glUseProgram(program_);
    glUniform2f(viewportLocation_, static_cast<float>(width), static_cast<float>(height));
    glBindVertexArray(vertexArray_);
    glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer_);
    glBufferData(GL_ARRAY_BUFFER, static_cast<GLsizeiptr>(vertices_.size() * sizeof(Vertex)), vertices_.data(), GL_DYNAMIC_DRAW);
    glDrawArrays(GL_TRIANGLES, 0, static_cast<GLsizei>(vertices_.size()));
    glBindVertexArray(0);
    glUseProgram(0);
}

void HudRenderer::handleTouch(int action, float x, float y, int width, int height) {
    if (width <= 0 || height <= 0) return;
    const float scale = std::min(width / 420.f, height / 780.f);
    const float virtualWidth = width / scale, virtualHeight = height / scale;
    const float movementX = x - touchX_;
    const float movementY = y - touchY_;
    const bool wasTouchActive = touchActive_;
    touchX_ = x; touchY_ = y;
    const float elapsed = std::chrono::duration<float>(std::chrono::steady_clock::now() - startTime_).count();
    const float toolboxTop = virtualHeight - 125.f;
    if (action == AMOTION_EVENT_ACTION_DOWN || action == AMOTION_EVENT_ACTION_POINTER_DOWN) {
        touchActive_ = true;
        if (turnFinished_) {
            requestIndex_ = score_ = mistakes_ = anticipations_ = 0;
            penaltySeconds_ = bonusSeconds_ = gauzesUsed_ = 0;
            gauzesRemaining_ = 12;
            suturePacks_ = 8;
            prolenePacks_ = 4;
            prolene40Packs_ = 1;
            rumelTourniquets_ = 3;
            silkTies_ = 6;
            ethbGreenPacks_ = 4;
            ethbWhitePacks_ = 4;
            sternalWires_ = 6;
            vicrylZeroPacks_ = 2;
            vicrylTwoZeroPacks_ = 2;
            monocrylThreeZeroPacks_ = 2;
            precision_ = 100;
            steadyMoves_ = 0;
            queuedTool_ = -1;
            countingGauze_ = turnFinished_ = false;
            feedback_.clear();
            feedbackUntil_ = 0.f;
            heartExposed_ = false;
            sterileDrapePlaced_ = false;
            aorticCannulaPlaced_ = false;
            cavalCannulaPlaced_ = false;
            blueRecoveryConnected_ = false;
            yellowVentConnected_ = false;
            aorticCardioplegiaPursePlaced_ = false;
            superiorCavalPursePlaced_ = false;
            inferiorCavalPursePlaced_ = false;
            aorticClampApplied_ = false;
            cardioplegiaLineConnected_ = false;
            coolingPlaced_ = false;
            mitralRetractorPlaced_ = false;
            sumpVentPlaced_ = false;
            rootVentPlaced_ = false;
            aorticClampReleased_ = false;
            cavalCannulaRemoved_ = false;
            aorticCannulaRemoved_ = false;
            preClosureCountComplete_ = false;
            pendingPlacementTool_ = -1;
            startTime_ = std::chrono::steady_clock::now();
            requestStartedAt_ = 0.f;
            gauzeCountGuess_ = 0;
            return;
        }
        if (pendingPlacementTool_ >= 0 && requestIndex_ < kRequestCount) {
            const float logicalX = x / scale;
            const float logicalY = y / scale;
            const float fieldTop = 210.f;
            const auto placement = kRequestSequence[requestIndex_].placement;
            bool placementCorrect = false;
            if (placement == PlacementAction::SterileDrape) {
                placementCorrect = logicalX > virtualWidth * .14f && logicalX < virtualWidth * .86f
                        && logicalY > fieldTop + 290.f && logicalY < fieldTop + 370.f;
            } else if (placement == PlacementAction::AorticPurse || placement == PlacementAction::AorticCardioplegiaPurse
                    || placement == PlacementAction::ApplyAorticClamp || placement == PlacementAction::ConnectCardioplegia
                    || placement == PlacementAction::RootVent || placement == PlacementAction::ReleaseAorticClamp
                    || placement == PlacementAction::DecannulateAorta) {
                placementCorrect = logicalX > virtualWidth * .58f && logicalX < virtualWidth * .82f
                        && logicalY > fieldTop + 125.f && logicalY < fieldTop + 210.f;
            } else if (placement == PlacementAction::SuperiorCavalPurse) {
                placementCorrect = logicalX > virtualWidth * .18f && logicalX < virtualWidth * .42f
                        && logicalY > fieldTop + 125.f && logicalY < fieldTop + 210.f;
            } else if (placement == PlacementAction::InferiorCavalPurse) {
                placementCorrect = logicalX > virtualWidth * .18f && logicalX < virtualWidth * .42f
                        && logicalY > fieldTop + 225.f && logicalY < fieldTop + 300.f;
            } else if (placement == PlacementAction::AorticCannula) {
                placementCorrect = logicalX > virtualWidth * .58f && logicalX < virtualWidth * .82f
                        && logicalY > fieldTop + 225.f && logicalY < fieldTop + 300.f;
            } else if (placement == PlacementAction::CavalCannula || placement == PlacementAction::DecannulateCaval) {
                placementCorrect = logicalX > virtualWidth * .18f && logicalX < virtualWidth * .42f
                        && logicalY > fieldTop + 310.f && logicalY < fieldTop + 385.f;
            } else if (placement == PlacementAction::ConnectBlueRecovery) {
                placementCorrect = logicalX > virtualWidth * .14f && logicalX < virtualWidth * .48f
                        && logicalY > fieldTop + 210.f && logicalY < fieldTop + 285.f;
            } else if (placement == PlacementAction::ConnectYellowVent) {
                placementCorrect = logicalX > virtualWidth * .52f && logicalX < virtualWidth * .86f
                        && logicalY > fieldTop + 210.f && logicalY < fieldTop + 285.f;
            } else if (placement == PlacementAction::PlaceIce) {
                placementCorrect = logicalX > virtualWidth * .2f && logicalX < virtualWidth * .8f
                        && logicalY > fieldTop + 290.f && logicalY < fieldTop + 380.f;
            } else if (placement == PlacementAction::MitralRetractor) {
                placementCorrect = logicalX > virtualWidth * .35f && logicalX < virtualWidth * .78f
                        && logicalY > fieldTop + 130.f && logicalY < fieldTop + 230.f;
            } else if (placement == PlacementAction::SumpVent) {
                placementCorrect = logicalX > virtualWidth * .62f && logicalX < virtualWidth * .9f
                        && logicalY > fieldTop + 235.f && logicalY < fieldTop + 305.f;
            }
            if (placementCorrect) {
                switch (placement) {
                    case PlacementAction::SterileDrape: sterileDrapePlaced_ = true; break;
                    case PlacementAction::AorticCardioplegiaPurse: aorticCardioplegiaPursePlaced_ = true; break;
                    case PlacementAction::SuperiorCavalPurse: superiorCavalPursePlaced_ = true; break;
                    case PlacementAction::InferiorCavalPurse: inferiorCavalPursePlaced_ = true; break;
                    case PlacementAction::AorticCannula: aorticCannulaPlaced_ = true; break;
                    case PlacementAction::CavalCannula: cavalCannulaPlaced_ = true; break;
                    case PlacementAction::ConnectBlueRecovery: blueRecoveryConnected_ = true; break;
                    case PlacementAction::ConnectYellowVent: yellowVentConnected_ = true; break;
                    case PlacementAction::ApplyAorticClamp: aorticClampApplied_ = true; break;
                    case PlacementAction::ConnectCardioplegia: cardioplegiaLineConnected_ = true; break;
                    case PlacementAction::PlaceIce: coolingPlaced_ = true; break;
                    case PlacementAction::MitralRetractor: mitralRetractorPlaced_ = true; break;
                    case PlacementAction::SumpVent: sumpVentPlaced_ = true; break;
                    case PlacementAction::RootVent: rootVentPlaced_ = true; break;
                    case PlacementAction::ReleaseAorticClamp: aorticClampReleased_ = true; break;
                    case PlacementAction::DecannulateCaval: cavalCannulaRemoved_ = true; break;
                    case PlacementAction::DecannulateAorta: aorticCannulaRemoved_ = true; break;
                    default: break;
                }
                pendingPlacementTool_ = -1;
                deliverCurrentRequest(elapsed, false);
            } else {
                score_ = std::max(0, score_ - 50);
                penaltySeconds_ += 8;
                ++mistakes_;
                precision_ = std::max(55, precision_ - 5);
                feedback_ = "CONTROLLA POSIZIONE";
                feedbackUntil_ = elapsed + 2.f;
            }
            return;
        }
        if (countingGauze_ && y / scale >= virtualHeight - 205.f && y / scale < toolboxTop) {
            const float logicalX = x / scale;
            if (logicalX < 97.f) {
                gauzeCountGuess_ = std::max(0, gauzeCountGuess_ - 1);
            } else if (logicalX < virtualWidth - 119.f) {
                gauzeCountGuess_ = std::min(12, gauzeCountGuess_ + 1);
            } else if (gauzeCountGuess_ == gauzesUsed_) {
                score_ += 300;
                ++requestIndex_;
                countingGauze_ = false;
                preClosureCountComplete_ = true;
                gauzeCountGuess_ = 0;
                feedback_ = "CONTEGGIO OK";
                requestStartedAt_ = elapsed;
                feedbackUntil_ = elapsed + 2.f;
            } else {
                score_ = std::max(0, score_ - 100);
                penaltySeconds_ += 20;
                ++mistakes_;
                precision_ = std::max(55, precision_ - 8);
                feedback_ = "RICONTROLLA GARZE";
                feedbackUntil_ = elapsed + 2.5f;
            }
        } else if (!countingGauze_ && !turnFinished_ && y / scale >= toolboxTop) {
            constexpr float margin = 12.f;
            const int targetTool = requestIndex_ < kRequestCount
                ? kRequestSequence[requestIndex_].tool : selectedTool_;
            const int maxStart = kToolCount - kVisibleToolSlots;
            const int firstVisibleTool = std::max(0, std::min(maxStart, targetTool - kVisibleToolSlots / 2));
            const float slotWidth = (virtualWidth - margin * 2.f) / kVisibleToolSlots;
            const int slot = std::max(0, std::min(kVisibleToolSlots - 1,
                static_cast<int>((x / scale - margin) / slotWidth)));
            const int tool = firstVisibleTool + slot;
            selectedTool_ = tool;
            selectTool(tool, elapsed);
        }
    } else if (action == AMOTION_EVENT_ACTION_MOVE) {
        touchActive_ = true;
        if (wasTouchActive && std::sqrt(movementX * movementX + movementY * movementY) > 28.f * scale) {
            precision_ = std::max(55, precision_ - 2);
            steadyMoves_ = 0;
        } else if (wasTouchActive && ++steadyMoves_ >= 8) {
            precision_ = std::min(100, precision_ + 1);
            steadyMoves_ = 0;
        }
    } else if (action == AMOTION_EVENT_ACTION_UP || action == AMOTION_EVENT_ACTION_POINTER_UP || action == AMOTION_EVENT_ACTION_CANCEL) {
        touchActive_ = false;
    }
}

void HudRenderer::drawFrame(float width, float height, float elapsedSeconds) {
    if (!turnFinished_ && !countingGauze_ && requestIndex_ < kRequestCount
            && elapsedSeconds - requestStartedAt_ >= 15.f) {
        score_ = std::max(0, score_ - 50);
        penaltySeconds_ += 10;
        ++mistakes_;
        ++requestIndex_;
        queuedTool_ = -1;
        pendingPlacementTool_ = -1;
        requestStartedAt_ = elapsedSeconds;
        feedback_ = "RICHIESTA SCADUTA";
        feedbackUntil_ = elapsedSeconds + 2.f;
        if (requestIndex_ == 10) heartExposed_ = true;
        if (requestIndex_ < kRequestCount
                && kRequestSequence[requestIndex_].placement == PlacementAction::StartFinalCount) {
            countingGauze_ = true;
            gauzeCountGuess_ = 0;
        }
        if (requestIndex_ >= kRequestCount) finishTurn(false);
    }
        const float progress = std::max(0.f, std::min(1.f,
            (elapsedSeconds + penaltySeconds_ - bonusSeconds_) / 480.f));
    const int heartRate = 72 + static_cast<int>(progress * 48.f);
    const int systolic = 120 - static_cast<int>(progress * 32.f);
    const int diastolic = 80 - static_cast<int>(progress * 20.f);
    const int remaining = std::max(0, 480 - static_cast<int>(elapsedSeconds) - penaltySeconds_ + bonusSeconds_);
    if (remaining == 0 && !turnFinished_) {
        finishTurn(false);
        feedback_ = "TEMPO ESAURITO";
    }
    const int precision = precision_;
    const Color vitalColor = progress < 0.55f ? kGreen : (progress < 0.82f ? kAmber : kRed);
    constexpr float margin = 12.f;
    const float panelWidth = std::min((width - margin * 3.f) * 0.5f, 190.f);
    constexpr float panelHeight = 94.f;
    const float rightX = width - margin - panelWidth;
    drawRect(margin, margin, panelWidth, panelHeight, kPanel);
    drawRect(rightX, margin, panelWidth, panelHeight, kPanel);
    drawRect(margin, margin, panelWidth, 2.f, vitalColor);
    drawRect(rightX, margin, panelWidth, 2.f, kMuted);
    drawText("PARAMETRI", margin + 9.f, margin + 8.f, 1.55f, kMuted);
    char vitalText[24]{};
    std::snprintf(vitalText, sizeof(vitalText), "HR %d BPM", heartRate);
    drawText(vitalText, margin + 9.f, margin + 25.f, 1.8f, kWhite);
    std::snprintf(vitalText, sizeof(vitalText), "BP %d/%d", systolic, diastolic);
    drawText(vitalText, margin + 9.f, margin + 42.f, 1.55f, vitalColor);

    const float graphLeft = margin + 9.f, graphWidth = panelWidth - 18.f, baseline = margin + 77.f;
    const float phase = std::fmod(elapsedSeconds * heartRate / 60.f, 1.f);
    float oldX = graphLeft, oldY = baseline;
    for (int index = 1; index <= 96; ++index) {
        const float x = graphLeft + graphWidth * index / 96.f;
        const float position = std::fmod(index / 96.f * 2.8f + phase, 1.f);
        float pulse = 0.f;
        if (position > .45f && position < .5f) pulse = (position - .45f) * 4.f;
        else if (position >= .5f && position < .54f) pulse = (.54f - position) * 12.f;
        else if (position >= .54f && position < .59f) pulse = -(position - .54f) * 2.f;
        else if (position >= .59f && position < .72f) pulse = -.1f + std::sin((position - .59f) / .13f * 3.14159f) * .1f;
        const float y = baseline - pulse * 22.f;
        drawSegment(oldX, oldY, x, y, 1.25f, vitalColor);
        oldX=x; oldY=y;
    }

    drawText("PUNTI", rightX + 9.f, margin + 8.f, 1.55f, kMuted);
    char scoreText[16]{};
    std::snprintf(scoreText, sizeof(scoreText), "%d", score_);
    drawText(scoreText, rightX + 76.f, margin + 8.f, 1.55f, kWhite);
    char timer[16]{};
    std::snprintf(timer, sizeof(timer), "%02d:%02d", remaining / 60, remaining % 60);
    drawText("TEMPO", rightX + 9.f, margin + 26.f, 1.4f, kMuted);
    drawText(timer, rightX + 81.f, margin + 22.f, 2.4f, remaining < 60 ? kRed : kWhite);
    char precisionText[16]{};
    std::snprintf(precisionText, sizeof(precisionText), "PREC %d%%", precision);
    drawText(precisionText, rightX + 9.f, margin + 50.f, 1.55f, kWhite);
    drawRect(rightX + 9.f, margin + 67.f, panelWidth - 18.f, 7.f, {.14f,.23f,.21f,1.f});
    drawRect(rightX + 9.f, margin + 67.f, (panelWidth - 18.f) * precision / 100.f, 7.f, precision > 70 ? kGreen : kAmber);

    const float requestTop = margin + panelHeight + 10.f;
    const float requestWidth = width - margin * 2.f;
    drawRect(margin, requestTop, requestWidth, 82.f, kPanel);
    drawRect(margin, requestTop, 3.f, 82.f, turnFinished_ ? kRed : kGreen);
    if (turnFinished_) {
        drawText("TURNO TERMINATO", margin + 12.f, requestTop + 12.f, 1.8f, kRed);
        drawText(feedback_.c_str(), margin + 12.f, requestTop + 42.f, 1.45f, kWhite);
        drawText(careerRank(), width - 134.f, requestTop + 61.f, 1.15f, kGreen);
    } else if (countingGauze_) {
        drawText("FASE FINALE", margin + 12.f, requestTop + 10.f, 1.45f, kMuted);
        drawText("CONTA LE GARZE USATE", margin + 12.f, requestTop + 34.f, 1.7f, kWhite);
    } else if (elapsedSeconds < feedbackUntil_) {
        drawText(feedback_.c_str(), margin + 12.f, requestTop + 15.f, 1.8f, kGreen);
        drawText("CHIRURGO CHIEDE", margin + 12.f, requestTop + 48.f, 1.25f, kMuted);
        drawText(requestLabel(requestIndex_), margin + 143.f, requestTop + 43.f, 1.2f, kWhite);
    } else if (requestIndex_ < kRequestCount) {
        drawText("CHIRURGO CHIEDE", margin + 12.f, requestTop + 11.f, 1.5f, kMuted);
        drawText(requestLabel(requestIndex_), margin + 12.f, requestTop + 37.f, 1.6f, kWhite);
        if (requestIndex_ + 1 < kRequestCount) {
            drawText("POI", width - 75.f, requestTop + 11.f, 1.1f, kMuted);
            drawText(requestLabel(requestIndex_ + 1), width - 106.f, requestTop + 34.f, .8f, kGreen);
        }
    }

    constexpr float toolboxHeight = 115.f;
    const float toolboxTop = height - toolboxHeight - 10.f;
    const float tableTop = requestTop + 94.f;
    const float tableBottom = toolboxTop - 12.f;
    if (tableBottom > tableTop + 60.f) {
        drawRect(margin, tableTop, width - margin * 2.f, tableBottom - tableTop, {0.04f,0.15f,0.13f,0.48f});
        drawRect(margin, tableTop, width - margin * 2.f, 2.f, {0.32f,0.65f,0.56f,0.65f});
        drawText("CAMPO STERILE", margin + 10.f, tableTop + 9.f, 1.4f, kMuted);
        drawText("GARZE", margin + 10.f, tableTop + 34.f, 1.1f, kMuted);
        char supplyText[16]{};
        std::snprintf(supplyText, sizeof(supplyText), "%02d", gauzesRemaining_);
        drawText(supplyText, margin + 58.f, tableTop + 31.f, 1.6f, kWhite);
        drawText("SUTURE", margin + 111.f, tableTop + 34.f, 1.1f, kMuted);
        std::snprintf(supplyText, sizeof(supplyText), "%02d", suturePacks_);
        drawText(supplyText, margin + 169.f, tableTop + 31.f, 1.6f, kWhite);
        drawText("PROL 3-0", margin + 203.f, tableTop + 34.f, 1.05f, kMuted);
        std::snprintf(supplyText, sizeof(supplyText), "%02d", prolenePacks_);
        drawText(supplyText, margin + 262.f, tableTop + 31.f, 1.6f, kWhite);
        drawText("ERRORI", width - 104.f, tableTop + 34.f, 1.1f, kMuted);
        std::snprintf(supplyText, sizeof(supplyText), "%02d", mistakes_);
        drawText(supplyText, width - 48.f, tableTop + 31.f, 1.6f, mistakes_ ? kAmber : kWhite);
        drawSegment(margin + 10.f, tableTop + 61.f, width - margin - 10.f, tableTop + 61.f, 1.f, {0.32f,0.65f,0.56f,0.45f});
        drawText("ANTICIPI", margin + 10.f, tableTop + 72.f, 1.1f, kMuted);
        std::snprintf(supplyText, sizeof(supplyText), "%02d", anticipations_);
        drawText(supplyText, margin + 82.f, tableTop + 69.f, 1.6f, kGreen);
        drawText("TEMPO BONUS", margin + 130.f, tableTop + 72.f, 1.1f, kMuted);
        std::snprintf(supplyText, sizeof(supplyText), "%02d SEC", bonusSeconds_);
        drawText(supplyText, width - 104.f, tableTop + 69.f, 1.15f, kWhite);
        drawText("GRADO", margin + 10.f, tableTop + 106.f, 1.1f, kMuted);
        drawText(careerRank(), margin + 57.f, tableTop + 102.f, 1.15f, kWhite);
        std::snprintf(supplyText, sizeof(supplyText), "EXP %d", careerPoints_);
        drawText(supplyText, width - 92.f, tableTop + 102.f, 1.1f, kGreen);
        if (heartExposed_) drawText("CUORE ESPOSTO", margin + 235.f, tableTop + 102.f, 1.f, kGreen);
        if (heartExposed_) {
            drawText("CEC", margin + 10.f, tableTop + 137.f, 1.2f, kMuted);
            drawText(sterileDrapePlaced_ ? "TELINO OK" : "TELINO DA POSARE", margin + 54.f, tableTop + 137.f, 1.f,
                    sterileDrapePlaced_ ? kGreen : kMuted);
            drawText("PROL 4-0", margin + 198.f, tableTop + 137.f, 1.f, kMuted);
            std::snprintf(supplyText, sizeof(supplyText), "%02d", prolene40Packs_);
            drawText(supplyText, margin + 255.f, tableTop + 134.f, 1.3f, kWhite);
            drawText("RUMEL", margin + 292.f, tableTop + 137.f, 1.f, kMuted);
            std::snprintf(supplyText, sizeof(supplyText), "%02d", rumelTourniquets_);
            drawText(supplyText, margin + 337.f, tableTop + 134.f, 1.3f, kWhite);
            drawText(aorticCardioplegiaPursePlaced_ ? "BORSA RADICE OK" : "BORSA RADICE --", margin + 10.f,
                tableTop + 162.f, 1.f, aorticCardioplegiaPursePlaced_ ? kGreen : kMuted);
            drawText(superiorCavalPursePlaced_ ? "VCS OK" : "VCS --", margin + 205.f, tableTop + 162.f, 1.f,
                superiorCavalPursePlaced_ ? kGreen : kMuted);
            drawText(inferiorCavalPursePlaced_ ? "VCI OK" : "VCI --", margin + 275.f, tableTop + 162.f, 1.f,
                inferiorCavalPursePlaced_ ? kGreen : kMuted);
            drawText(aorticCannulaPlaced_ ? "AORTA OK" : "AORTA --", margin + 10.f, tableTop + 188.f, 1.f,
                aorticCannulaPlaced_ ? kGreen : kMuted);
            drawText(cavalCannulaPlaced_ ? "CAVA OK" : "CAVA --", margin + 115.f, tableTop + 188.f, 1.f,
                cavalCannulaPlaced_ ? kGreen : kMuted);
            drawSegment(margin + 205.f, tableTop + 190.f, margin + 227.f, tableTop + 190.f, 4.f, kBlue);
            drawText(blueRecoveryConnected_ ? "BLU RECUPERO OK" : "BLU RECUPERO", margin + 234.f, tableTop + 184.f, .85f,
                    blueRecoveryConnected_ ? kGreen : kMuted);
            drawSegment(margin + 10.f, tableTop + 216.f, margin + 32.f, tableTop + 216.f, 4.f, kYellow);
            drawText(yellowVentConnected_ ? "GIALLO SUMP OK" : "GIALLO SUMP VENT", margin + 39.f, tableTop + 210.f, .9f,
                yellowVentConnected_ ? kGreen : kMuted);
            drawSegment(margin + 210.f, tableTop + 216.f, margin + 232.f, tableTop + 216.f, 4.f, kRed);
            drawText("ROSSO IN ATTESA", margin + 239.f, tableTop + 210.f, .9f, kMuted);
        }
    }

    if (heartExposed_ && !countingGauze_ && tableBottom > tableTop + 310.f && requestIndex_ < kRequestCount) {
        const auto placement = kRequestSequence[requestIndex_].placement;
        if (placement == PlacementAction::SterileDrape) {
            drawRect(width * .14f, tableTop + 290.f, width * .72f, 80.f, {0.12f,0.36f,0.3f,0.8f});
            drawText("TELINO SOTTO INCISIONE", width * .25f, tableTop + 322.f, 1.25f, kWhite);
        } else if (placement == PlacementAction::AorticPurse || placement == PlacementAction::AorticCardioplegiaPurse
                || placement == PlacementAction::ApplyAorticClamp || placement == PlacementAction::ConnectCardioplegia
                || placement == PlacementAction::RootVent || placement == PlacementAction::ReleaseAorticClamp
                || placement == PlacementAction::DecannulateAorta) {
            drawRect(width * .58f, tableTop + 125.f, width * .24f, 85.f, {0.12f,0.36f,0.3f,0.8f});
            drawText("RADICE AORTICA", width * .595f, tableTop + 158.f, .9f, kWhite);
        } else if (placement == PlacementAction::SuperiorCavalPurse) {
            drawRect(width * .18f, tableTop + 125.f, width * .24f, 85.f, {0.12f,0.36f,0.3f,0.8f});
            drawText("VCS", width * .255f, tableTop + 158.f, 1.2f, kWhite);
        } else if (placement == PlacementAction::InferiorCavalPurse) {
            drawRect(width * .18f, tableTop + 225.f, width * .24f, 75.f, {0.12f,0.36f,0.3f,0.8f});
            drawText("VCI", width * .255f, tableTop + 254.f, 1.2f, kWhite);
        } else if (placement == PlacementAction::AorticCannula) {
            drawRect(width * .58f, tableTop + 225.f, width * .24f, 75.f, {0.12f,0.36f,0.3f,0.8f});
            drawText("AORTA", width * .63f, tableTop + 254.f, 1.2f, kWhite);
        } else if (placement == PlacementAction::CavalCannula || placement == PlacementAction::DecannulateCaval) {
            drawRect(width * .18f, tableTop + 310.f, width * .24f, 75.f, {0.12f,0.36f,0.3f,0.8f});
            drawText("CAVA", width * .24f, tableTop + 339.f, 1.2f, kWhite);
        } else if (placement == PlacementAction::ConnectBlueRecovery) {
            drawRect(width * .14f, tableTop + 210.f, width * .34f, 75.f, {0.09f,0.27f,0.43f,0.85f});
            drawText("ASPIRATORE RECUPERO", width * .18f, tableTop + 240.f, .95f, kWhite);
        } else if (placement == PlacementAction::ConnectYellowVent) {
            drawRect(width * .52f, tableTop + 210.f, width * .34f, 75.f, {0.38f,0.32f,0.08f,0.85f});
            drawText("SUMP VENT", width * .61f, tableTop + 240.f, 1.1f, kWhite);
        } else if (placement == PlacementAction::PlaceIce) {
            drawRect(width * .2f, tableTop + 290.f, width * .6f, 85.f, {0.2f,0.47f,0.52f,0.7f});
            drawText("GHIACCIO PERICARDICO", width * .29f, tableTop + 325.f, 1.05f, kWhite);
        } else if (placement == PlacementAction::MitralRetractor) {
            drawRect(width * .32f, tableTop + 130.f, width * .42f, 95.f, {0.12f,0.36f,0.3f,0.8f});
            drawText("ESPOSIZIONE MITRALE", width * .37f, tableTop + 168.f, 1.f, kWhite);
        } else if (placement == PlacementAction::SumpVent) {
            drawRect(width * .65f, tableTop + 235.f, width * .25f, 75.f, {0.12f,0.36f,0.3f,0.8f});
            drawText("SUMP", width * .72f, tableTop + 264.f, 1.2f, kWhite);
        }
    }

    if (countingGauze_ && !turnFinished_) {
        const float countTop = height - 205.f;
        drawRect(margin, countTop, width - margin * 2.f, 72.f, {0.025f,0.11f,0.1f,0.97f});
        const char *countMessage = elapsedSeconds < feedbackUntil_ ? feedback_.c_str() : "CONTA LE GARZE USATE";
        drawText(countMessage, margin + 10.f, countTop + 6.f, 1.25f, kWhite);
        drawRect(margin + 8.f, countTop + 29.f, 77.f, 34.f, {0.13f,0.28f,0.24f,1.f});
        drawText("-", margin + 42.f, countTop + 36.f, 2.f, kWhite);
        char countText[12]{};
        std::snprintf(countText, sizeof(countText), "%02d", gauzeCountGuess_);
        drawText(countText, width * 0.37f, countTop + 39.f, 2.f, kGreen);
        drawRect(width * 0.51f, countTop + 29.f, 68.f, 34.f, {0.13f,0.28f,0.24f,1.f});
        drawText("+", width * 0.51f + 30.f, countTop + 36.f, 2.f, kWhite);
        drawRect(width - 119.f, countTop + 29.f, 107.f, 34.f, {0.16f,0.42f,0.34f,1.f});
        drawText("CONFERMA", width - 109.f, countTop + 41.f, 1.05f, kWhite);
    }

    drawRect(margin, toolboxTop, width - margin * 2.f, toolboxHeight, kPanel);
    drawRect(margin, toolboxTop, width - margin * 2.f, 2.f, kMuted);
    drawText("CARRELLO STRUMENTI", margin + 9.f, toolboxTop + 7.f, 1.15f, kMuted);
        const int activeTool = requestIndex_ < kRequestCount
            ? kRequestSequence[requestIndex_].tool : selectedTool_;
        const int maxStart = kToolCount - kVisibleToolSlots;
    const int firstVisibleTool = std::max(0, std::min(maxStart, activeTool - kVisibleToolSlots / 2));
    const float slotWidth = (width - margin * 2.f) / kVisibleToolSlots;
    for (int slot=0; slot<kVisibleToolSlots; ++slot) {
        const int tool = firstVisibleTool + slot;
        const float slotX=margin+slotWidth*slot;
        const Color iconColor=tool==selectedTool_?kGreen:kMuted;
        if (tool==selectedTool_) drawRect(slotX+2.f,toolboxTop+24.f,slotWidth-4.f,82.f,{.11f,.27f,.23f,.95f});
        if (!turnFinished_ && !countingGauze_ && requestIndex_ < kRequestCount
            && tool == kRequestSequence[requestIndex_].tool)
            drawRect(slotX+2.f,toolboxTop+23.f,slotWidth-4.f,2.f,kGreen);
        const Color itemColor = tool == 19 ? kBlue : (tool == 20 ? kYellow
            : (tool == 41 ? kGreen : (tool == 42 ? kWhite : iconColor)));
        drawToolIcon(tool,slotX+slotWidth*.5f,toolboxTop+54.f,11.f,itemColor);
        const float textWidth=static_cast<float>(std::strlen(kToolLabels[tool]))*4.2f;
        drawText(kToolLabels[tool],slotX+(slotWidth-textWidth)*.5f,toolboxTop+76.f,.7f,tool==selectedTool_?kWhite:kMuted);
    }
}

void HudRenderer::drawRect(float x,float y,float width,float height,Color color) {
    const Vertex a{x,y,color.r,color.g,color.b,color.a}, b{x+width,y,color.r,color.g,color.b,color.a};
    const Vertex c{x+width,y+height,color.r,color.g,color.b,color.a}, d{x,y+height,color.r,color.g,color.b,color.a};
    vertices_.insert(vertices_.end(),{a,b,c,a,c,d});
}

void HudRenderer::drawSegment(float x1,float y1,float x2,float y2,float thickness,Color color) {
    const float dx=x2-x1,dy=y2-y1,length=std::sqrt(dx*dx+dy*dy);
    if(length<.001f)return;
    const float ox=-dy/length*thickness*.5f,oy=dx/length*thickness*.5f;
    const Vertex a{x1+ox,y1+oy,color.r,color.g,color.b,color.a},b{x2+ox,y2+oy,color.r,color.g,color.b,color.a};
    const Vertex c{x2-ox,y2-oy,color.r,color.g,color.b,color.a},d{x1-ox,y1-oy,color.r,color.g,color.b,color.a};
    vertices_.insert(vertices_.end(),{a,b,c,a,c,d});
}

void HudRenderer::drawText(const char *text,float x,float y,float cell,Color color) {
    float cursor=x;
    for(const char *c=text;c&&*c;++c){
        const auto rows=glyph(*c);
        for(int row=0;row<7;++row)for(int col=0;col<5;++col)
            if(rows[row]&(1u<<(4-col)))drawRect(cursor+col*cell,y+row*cell,cell,cell,color);
        cursor+=cell*6.f;
    }
}

void HudRenderer::drawToolIcon(int tool,float cx,float cy,float size,Color color) {
    if(tool==0){
        drawSegment(cx-size*.55f,cy+size*.7f,cx+size*.45f,cy-size*.35f,size*.18f,color);
        drawSegment(cx+size*.45f,cy-size*.35f,cx+size*.72f,cy-size*.62f,size*.16f,color);
        drawSegment(cx-size*.55f,cy+size*.7f,cx-size*.76f,cy+size*.88f,size*.16f,color);
    }else if(tool==1){
        drawSegment(cx-size*.65f,cy+size*.65f,cx+size*.35f,cy-size*.35f,size*.28f,color);
        drawSegment(cx+size*.35f,cy-size*.35f,cx+size*.72f,cy-size*.72f,size*.12f,color);
    }else if(tool==2){
        drawSegment(cx-size*.68f,cy+size*.58f,cx+size*.4f,cy-size*.35f,size*.22f,color);
        drawSegment(cx+size*.4f,cy-size*.35f,cx+size*.73f,cy-size*.65f,size*.3f,color);
        drawSegment(cx-size*.24f,cy+size*.23f,cx+size*.08f,cy+size*.57f,size*.12f,color);
    }else if(tool==3){
        drawRect(cx-size*.52f,cy-size*.52f,size*1.04f,size*1.04f,color);
        drawRect(cx-size*.11f,cy-size*.36f,size*.22f,size*.72f,{.025f,.09f,.085f,1.f});
        drawRect(cx-size*.36f,cy-size*.11f,size*.72f,size*.22f,{.025f,.09f,.085f,1.f});
    }else if(tool==4){
        drawSegment(cx-size*.65f,cy+size*.5f,cx-size*.2f,cy-size*.45f,size*.13f,color);
        drawSegment(cx-size*.2f,cy-size*.45f,cx+size*.2f,cy+size*.5f,size*.13f,color);
        drawSegment(cx+size*.2f,cy+size*.5f,cx+size*.65f,cy-size*.45f,size*.13f,color);
    }else if(tool==5){
        drawSegment(cx-size*.65f,cy,cx-size*.3f,cy-size*.3f,size*.16f,color);
        drawSegment(cx-size*.3f,cy-size*.3f,cx+size*.3f,cy-size*.3f,size*.16f,color);
        drawSegment(cx+size*.3f,cy-size*.3f,cx+size*.65f,cy,size*.16f,color);
        drawSegment(cx-size*.65f,cy,cx-size*.3f,cy+size*.3f,size*.16f,color);
        drawSegment(cx-size*.3f,cy+size*.3f,cx+size*.3f,cy+size*.3f,size*.16f,color);
        drawSegment(cx+size*.3f,cy+size*.3f,cx+size*.65f,cy,size*.16f,color);
    }else if(tool==6){
        drawSegment(cx-size*.8f,cy+size*.55f,cx-size*.18f,cy-size*.18f,size*.38f,color);
        drawSegment(cx-size*.1f,cy-size*.14f,cx+size*.72f,cy-size*.14f,size*.18f,color);
        for(int i=0;i<4;++i)drawSegment(cx+size*.02f+i*size*.17f,cy-size*.1f,cx+size*.12f+i*size*.17f,cy+size*.14f,size*.11f,color);
    }else if(tool==7){
        drawSegment(cx-size*.65f,cy,cx-size*.25f,cy-size*.38f,size*.18f,color);
        drawSegment(cx-size*.25f,cy-size*.38f,cx+size*.25f,cy-size*.38f,size*.18f,color);
        drawSegment(cx+size*.25f,cy-size*.38f,cx+size*.65f,cy,size*.18f,color);
        drawSegment(cx-size*.65f,cy,cx-size*.25f,cy+size*.38f,size*.18f,color);
        drawSegment(cx-size*.25f,cy+size*.38f,cx+size*.25f,cy+size*.38f,size*.18f,color);
        drawSegment(cx+size*.25f,cy+size*.38f,cx+size*.65f,cy,size*.18f,color);
    }else if(tool==8){
        drawSegment(cx-size*.65f,cy+size*.65f,cx-size*.2f,cy-size*.6f,size*.16f,color);
        drawSegment(cx+size*.65f,cy+size*.65f,cx+size*.2f,cy-size*.6f,size*.16f,color);
        drawSegment(cx-size*.2f,cy-size*.6f,cx+size*.2f,cy-size*.6f,size*.18f,color);
        drawSegment(cx-size*.65f,cy+size*.65f,cx+size*.65f,cy+size*.65f,size*.18f,color);
    }else if(tool==9){
        drawSegment(cx-size*.65f,cy-size*.3f,cx-size*.15f,cy+size*.25f,size*.14f,color);
        drawSegment(cx-size*.15f,cy+size*.25f,cx+size*.3f,cy-size*.25f,size*.14f,color);
        drawSegment(cx+size*.3f,cy-size*.25f,cx+size*.65f,cy+size*.15f,size*.14f,color);
        drawSegment(cx-size*.2f,cy-size*.65f,cx+size*.2f,cy-size*.65f,size*.14f,color);
        drawSegment(cx-size*.2f,cy-size*.65f,cx-size*.2f,cy-size*.35f,size*.14f,color);
        drawSegment(cx+size*.2f,cy-size*.65f,cx+size*.2f,cy-size*.35f,size*.14f,color);
    }else if(tool==10){
        drawSegment(cx-size*.7f,cy+size*.45f,cx+size*.7f,cy+size*.45f,size*.18f,color);
        drawSegment(cx-size*.5f,cy+size*.35f,cx-size*.5f,cy-size*.55f,size*.16f,color);
        drawSegment(cx+size*.5f,cy+size*.35f,cx+size*.5f,cy-size*.55f,size*.16f,color);
        drawSegment(cx-size*.5f,cy-size*.55f,cx-size*.25f,cy-size*.35f,size*.16f,color);
        drawSegment(cx+size*.5f,cy-size*.55f,cx+size*.25f,cy-size*.35f,size*.16f,color);
    }else if(tool==11){
        drawRect(cx-size*.8f,cy-size*.55f,size*1.6f,size*1.1f,color);
        drawSegment(cx-size*.8f,cy-size*.55f,cx,cy-size*.05f,size*.12f,kWhite);
    }else if(tool==12){
        drawSegment(cx-size*.65f,cy+size*.7f,cx+size*.55f,cy-size*.55f,size*.13f,color);
        drawSegment(cx-size*.4f,cy+size*.82f,cx+size*.75f,cy-size*.35f,size*.13f,color);
        drawSegment(cx+size*.55f,cy-size*.55f,cx+size*.78f,cy-size*.78f,size*.1f,color);
    }else if(tool==13){
        drawSegment(cx-size*.5f,cy+size*.75f,cx+size*.5f,cy-size*.6f,size*.14f,color);
        drawSegment(cx+size*.5f,cy+size*.75f,cx-size*.5f,cy-size*.6f,size*.14f,color);
        drawSegment(cx-size*.5f,cy-size*.6f,cx-size*.7f,cy-size*.8f,size*.12f,color);
        drawSegment(cx+size*.5f,cy-size*.6f,cx+size*.7f,cy-size*.8f,size*.12f,color);
    }else if(tool==14){
        drawRect(cx-size*.6f,cy-size*.45f,size*1.2f,size*.9f,color);
        drawRect(cx-size*.33f,cy-size*.18f,size*.66f,size*.36f,kPanel);
        drawSegment(cx-size*.75f,cy,cx-size*.6f,cy,size*.14f,color);
        drawSegment(cx+size*.6f,cy,cx+size*.75f,cy,size*.14f,color);
    }else if(tool==15){
        drawSegment(cx-size*.65f,cy-size*.65f,cx+size*.6f,cy+size*.55f,size*.13f,color);
        drawSegment(cx+size*.65f,cy-size*.65f,cx-size*.6f,cy+size*.55f,size*.13f,color);
        drawSegment(cx-size*.65f,cy-size*.65f,cx-size*.3f,cy-size*.8f,size*.13f,color);
        drawSegment(cx+size*.65f,cy-size*.65f,cx+size*.3f,cy-size*.8f,size*.13f,color);
    }else if(tool==16){
        drawSegment(cx-size*.55f,cy-size*.65f,cx+size*.45f,cy-size*.65f,size*.14f,color);
        drawSegment(cx+size*.45f,cy-size*.65f,cx+size*.45f,cy+size*.55f,size*.14f,color);
        drawSegment(cx+size*.45f,cy+size*.55f,cx-size*.45f,cy+size*.55f,size*.14f,color);
        drawSegment(cx-size*.45f,cy+size*.55f,cx-size*.45f,cy-size*.65f,size*.14f,color);
        drawSegment(cx-size*.45f,cy-size*.1f,cx+size*.5f,cy-size*.1f,size*.14f,color);
    }else if(tool==17){
        drawSegment(cx,cy+size*.75f,cx,cy-size*.5f,size*.24f,color);
        drawSegment(cx-size*.2f,cy-size*.5f,cx+size*.2f,cy-size*.5f,size*.14f,color);
        drawSegment(cx,cy-size*.5f,cx+size*.25f,cy-size*.75f,size*.14f,color);
    }else if(tool==18){
        drawSegment(cx,cy+size*.75f,cx,cy-size*.05f,size*.2f,color);
        drawSegment(cx,cy-size*.05f,cx-size*.55f,cy-size*.65f,size*.17f,color);
        drawSegment(cx,cy-size*.05f,cx+size*.55f,cy-size*.65f,size*.17f,color);
    }else if(tool==19){
        drawSegment(cx-size*.7f,cy+size*.25f,cx-size*.2f,cy+size*.25f,size*.18f,color);
        drawSegment(cx-size*.2f,cy+size*.25f,cx+size*.15f,cy-size*.25f,size*.18f,color);
        drawSegment(cx+size*.15f,cy-size*.25f,cx+size*.65f,cy-size*.25f,size*.18f,color);
    }else{
        drawSegment(cx-size*.65f,cy-size*.4f,cx-size*.15f,cy+size*.4f,size*.18f,color);
        drawSegment(cx-size*.15f,cy+size*.4f,cx+size*.2f,cy+size*.4f,size*.18f,color);
        drawSegment(cx+size*.2f,cy+size*.4f,cx+size*.65f,cy-size*.4f,size*.18f,color);
    }
}

const char *HudRenderer::toolName(int tool) const {
    return kToolNames[std::max(0, std::min(kToolCount - 1, tool))];
}

const char *HudRenderer::requestLabel(int requestIndex) const {
    if (requestIndex == 6) return "PUNTO CUTE 1/2";
    if (requestIndex == 7) return "PUNTO CUTE 2/2";
    if (requestIndex == 8) return "TI-CRON 1 SOSP 1/2";
    if (requestIndex == 9) return "TI-CRON 1 SOSP 2/2";
    if (requestIndex == 10) return "TELINO SOTTO INCISIONE";
    if (requestIndex == 11) return "PORTA AGHI LUNGO";
    if (requestIndex == 12) return "PINZA DEBAKEY";
    if (requestIndex == 13) return "PREPARA PROLENE 3-0";
    if (requestIndex == 14) return "BORSA AORTICA EST 1/2";
    if (requestIndex == 15) return "BORSA AORTICA INT 2/2";
    if (requestIndex == 16) return "PORTAGHI CARDIOPLEGIA";
    if (requestIndex == 17) return "DEBAKEY RADICE AORTICA";
    if (requestIndex == 18) return "TOURNEQUET CARDIOPLEGIA";
    if (requestIndex == 19) return "BORSA CARDIOPLEGIA PROLENE 4-0";
    if (requestIndex == 20) return "FORBICI DA FILO";
    if (requestIndex == 21) return "PORTAGHI PER CAVALI";
    if (requestIndex == 22) return "DEBAKEY PER CAVALI";
    if (requestIndex == 23) return "TOURNEQUET VCS VCI X2";
    if (requestIndex == 24) return "BORSA VCS PROLENE 3-0";
    if (requestIndex == 25) return "BORSA VCI PROLENE 3-0";
    if (requestIndex == 26) return "LACCIO TI-CRON";
    if (requestIndex == 27) return "POSIZIONA CANNULA AORTICA";
    if (requestIndex == 28) return "POSIZIONA CANNULA CAVA";
    if (requestIndex == 29) return "TUBO BLU: ASPIRATORE RECUPERO";
    if (requestIndex == 30) return "TUBO GIALLO: SUMP VENT";
    if (requestIndex < 0 || requestIndex >= kRequestCount) return "FINE TURNO";
    const auto &step = kRequestSequence[requestIndex];
    return step.label ? step.label : toolName(step.tool);
}

const char *HudRenderer::careerRank() const {
    if (careerPoints_ >= 8000) return "COORDINATORE";
    if (careerPoints_ >= 4500) return "ESPERTO";
    if (careerPoints_ >= 1800) return "AUTONOMO";
    if (careerPoints_ >= 500) return "JUNIOR";
    return "NEODIPLOMATO";
}

void HudRenderer::finishTurn(bool completed) {
    if (turnFinished_) return;
    turnFinished_ = true;
    if (completed) ++careerPoints_;
    careerPoints_ += std::max(0, score_);
    if (!careerDataFile_.empty()) {
        std::ofstream savedCareer(careerDataFile_, std::ios::trunc);
        savedCareer << careerPoints_;
    }
    if (!lastScoreFile_.empty()) {
        std::ofstream lastScore(lastScoreFile_, std::ios::trunc);
        lastScore << score_;
    }
    if (activity_ && activity_->vm && activity_->javaGameActivity) {
        JNIEnv *env = nullptr;
        bool attachedThread = false;
        const jint envStatus = activity_->vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
        if (envStatus == JNI_EDETACHED) {
            if (activity_->vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
            attachedThread = true;
        } else if (envStatus != JNI_OK) {
            return;
        }
        jclass activityClass = env->GetObjectClass(activity_->javaGameActivity);
        jmethodID finishMethod = env->GetMethodID(activityClass, "finish", "()V");
        if (finishMethod) env->CallVoidMethod(activity_->javaGameActivity, finishMethod);
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(activityClass);
        if (attachedThread) activity_->vm->DetachCurrentThread();
    }
}

void HudRenderer::selectTool(int tool, float elapsedSeconds) {
    if (requestIndex_ >= kRequestCount || countingGauze_) return;
    const RequestStep &step = kRequestSequence[requestIndex_];
    if (tool == step.tool) {
        if (step.placement != PlacementAction::None && step.placement != PlacementAction::StartFinalCount) {
            pendingPlacementTool_ = tool;
            feedback_ = step.label;
            feedbackUntil_ = elapsedSeconds + 8.f;
            return;
        }
        deliverCurrentRequest(elapsedSeconds, false);
        return;
    }
    const bool nextCanQueue = requestIndex_ + 1 < kRequestCount
            && kRequestSequence[requestIndex_ + 1].placement == PlacementAction::None;
    const int next = nextCanQueue ? kRequestSequence[requestIndex_ + 1].tool : -1;
    if (tool == next && queuedTool_ == -1) {
        queuedTool_ = tool;
        score_ += 75;
        ++anticipations_;
        feedback_ = "ANTICIPO PRONTO";
        feedbackUntil_ = elapsedSeconds + 2.f;
        return;
    }
    score_ = std::max(0, score_ - 50);
    penaltySeconds_ += 8;
    ++mistakes_;
    precision_ = std::max(55, precision_ - 8);
    feedback_ = "STRUMENTO ERRATO";
    feedbackUntil_ = elapsedSeconds + 2.f;
}

void HudRenderer::deliverCurrentRequest(float elapsedSeconds, bool anticipated) {
    const int completedRequest = requestIndex_;
    const RequestStep &step = kRequestSequence[completedRequest];
    const float responseTime = elapsedSeconds - requestStartedAt_;
    score_ += 100 + std::max(0, 80 - static_cast<int>(responseTime * 8.f)) + (anticipated ? 120 : 0);
    bonusSeconds_ += anticipated ? 8 : 3;
    const int stockCount = std::max(1, step.stockCount);
    switch (step.stock) {
        case StockItem::Gauze:
            gauzesUsed_ += stockCount;
            gauzesRemaining_ = std::max(0, gauzesRemaining_ - stockCount);
            break;
        case StockItem::Suture: suturePacks_ = std::max(0, suturePacks_ - stockCount); break;
        case StockItem::Prolene3: prolenePacks_ = std::max(0, prolenePacks_ - stockCount); break;
        case StockItem::Prolene4: prolene40Packs_ = std::max(0, prolene40Packs_ - stockCount); break;
        case StockItem::Rumel: rumelTourniquets_ = std::max(0, rumelTourniquets_ - stockCount); break;
        case StockItem::Silk: silkTies_ = std::max(0, silkTies_ - stockCount); break;
        case StockItem::EthibondGreen: ethbGreenPacks_ = std::max(0, ethbGreenPacks_ - stockCount); break;
        case StockItem::EthibondWhite: ethbWhitePacks_ = std::max(0, ethbWhitePacks_ - stockCount); break;
        case StockItem::SternalWire: sternalWires_ = std::max(0, sternalWires_ - stockCount); break;
        case StockItem::VicrylZero: vicrylZeroPacks_ = std::max(0, vicrylZeroPacks_ - stockCount); break;
        case StockItem::VicrylTwoZero: vicrylTwoZeroPacks_ = std::max(0, vicrylTwoZeroPacks_ - stockCount); break;
        case StockItem::MonocrylThreeZero: monocrylThreeZeroPacks_ = std::max(0, monocrylThreeZeroPacks_ - stockCount); break;
        case StockItem::None: break;
    }
    ++requestIndex_;
    if (completedRequest == 9) heartExposed_ = true;
    requestStartedAt_ = elapsedSeconds;
    const int queuedTool = queuedTool_;
    queuedTool_ = -1;
    feedback_ = anticipated ? "ANTICIPO CONSEGNATO" : "STRUMENTO CONSEGNATO";
    feedbackUntil_ = elapsedSeconds + 1.8f;
    if (requestIndex_ < kRequestCount
            && kRequestSequence[requestIndex_].placement == PlacementAction::StartFinalCount) {
        countingGauze_ = true;
        gauzeCountGuess_ = 0;
        feedback_ = "CONTEGGIO FINALE";
        return;
    }
    if (requestIndex_ >= kRequestCount) {
        feedback_ = "INTERVENTO COMPLETATO";
        finishTurn(true);
        return;
    }
    if (queuedTool == kRequestSequence[requestIndex_].tool) {
        deliverCurrentRequest(elapsedSeconds, true);
    }
}

const char *HudRenderer::requestLabelRecord(int requestIndex) const {
    if (requestIndex < 0 || requestIndex >= kRequestCount) return "FINE TURNO";
    const auto &step = kRequestSequence[requestIndex];
    return step.label ? step.label : toolName(step.tool);
}