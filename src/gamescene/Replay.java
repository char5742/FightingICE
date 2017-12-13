package gamescene;

import static org.lwjgl.glfw.GLFW.*;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import enumerate.GameSceneName;
import fighting.Fighting;
import input.KeyData;
import input.Keyboard;
import manager.GraphicManager;
import manager.InputManager;
import manager.SoundManager;
import setting.FlagSetting;
import setting.GameSetting;
import setting.LaunchSetting;
import struct.FrameData;
import struct.Key;
import struct.ScreenData;
import util.ResourceDrawer;

/** リプレイの再生を行うクラス */
public class Replay extends GameScene {

	/** 対戦処理を行うクラスのインスタンス */
	private Fighting fighting;

	/** Replayファイルからログを読み込むための入力ストリーム */
	protected DataInputStream dis;

	/** 現在のフレームナンバー */
	private int nowFrame;

	/** 各ラウンド前に行う初期化処理内における経過フレーム数 */
	private int elapsedBreakTime;

	/** 現在のラウンド */
	private int currentRound;

	/** 各ラウンドの開始時かどうかを表すフラグ */
	private boolean roundStartFlag;

	/** 対戦処理後のキャラクターデータといったゲーム情報を格納したフレームデータ */
	protected FrameData frameData;

	/** 対戦処理後のゲーム画面の情報 */
	private ScreenData screenData;

	/** 対戦処理に用いるP1, P2の入力情報 */
	private KeyData keyData;

	/** 再生速度を指定するインデックス */
	private int playSpeedIndex;

	/** 再生速度を管理する配列 */
	private int[] playSpeedArray;

	/** ラウンドが終わったかどうかを表すフラグ */
	private boolean isFinished;

	/**
	 * Replayシーンを初期化するコンストラクタ<br>
	 * このコンストラクタ内で読み込むReplayファイルをopenする.
	 */
	public Replay() {
		// 以下4行の処理はgamesceneパッケージ内クラスのコンストラクタには必ず含める
		this.gameSceneName = GameSceneName.REPLAY;
		this.isGameEndFlag = false;
		this.isTransitionFlag = false;
		this.nextGameScene = null;
		//////////////////////////////////////

		try {
			String path = "./log/replay/" + LaunchSetting.replayName + ".dat";
			this.dis = new DataInputStream(new FileInputStream(new File(path)));
			readHeader();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void initialize() {
		InputManager.getInstance().setSceneName(GameSceneName.REPLAY);

		this.fighting = new Fighting();
		this.fighting.initialize();

		this.nowFrame = 0;
		this.elapsedBreakTime = 0;
		this.currentRound = 1;
		this.roundStartFlag = true;

		this.frameData = new FrameData();
		this.screenData = new ScreenData();
		this.keyData = new KeyData();
		this.playSpeedIndex = 1;
		this.playSpeedArray = new int[] { 0, 1, 2, 4 };
		this.isFinished = false;

		SoundManager.getInstance().play(SoundManager.getInstance().getBackGroundMusic());
	}

	@Override
	public void update() {
		if (this.currentRound <= GameSetting.ROUND_MAX) {
			// ラウンド開始時に初期化
			if (this.roundStartFlag) {
				initRound();

			} else if (this.elapsedBreakTime < GameSetting.BREAKTIME_FRAME_NUMBER) {
				// break time
				processingBreakTime();
				this.elapsedBreakTime++;

			} else {
				// 再生速度を更新する
				updatePlaySpeed();

				// processing
				for (int i = 0; i < this.playSpeedArray[this.playSpeedIndex] && !this.isFinished; i++) {
					processingGame();
					// 体力が0orタイムオーバーならラウンド終了処理
					if (isBeaten() || isTimeOver()) {
						processingRoundEnd();
						this.nowFrame++;
						break;
					}
					this.nowFrame++;
				}

				// 画面をDrawerクラスで描画
				ResourceDrawer.getInstance().drawResource(this.fighting.getCharacters(),
						this.fighting.getProjectileDeque(), this.fighting.getHitEffectList(),
						this.frameData.getRemainingTimeMilliseconds(), this.currentRound);

				GraphicManager.getInstance().drawString("PlaySpeed:" + this.playSpeedArray[this.playSpeedIndex], 50,
						550);

				this.screenData = new ScreenData();
			}

		} else {
			// BGMを止める
			SoundManager.getInstance().stop(SoundManager.getInstance().getBackGroundMusic());
			transitionProcess();
		}

		if (Keyboard.getKeyDown(GLFW_KEY_ESCAPE)) {
			// BGMを止める
			SoundManager.getInstance().stop(SoundManager.getInstance().getBackGroundMusic());
			transitionProcess();
		}

	}

	@Override
	public void close() {
		this.fighting = null;
		this.frameData = null;
		this.screenData = null;
		this.keyData = null;

		try {
			this.dis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/** 各ラウンド開始時における, インターバル処理を行う */
	private void processingBreakTime() {
		GraphicManager.getInstance().drawQuad(0, 0, GameSetting.STAGE_WIDTH, GameSetting.STAGE_HEIGHT, 0, 0, 0, 0);
		GraphicManager.getInstance().drawString("Waiting for Round Start", 350, 200);
	}

	/**
	 * 対戦処理を行う.<br>
	 *
	 * 1. P1, P2の入力を受け取る.<br>
	 * 2. 対戦処理を行う.<br>
	 * 3. 対戦後のFrameData, 及びScreenDataを取得する.<br>
	 * 4. ラウンドが終了しているか判定する.<br>
	 */
	private void processingGame() {
		this.keyData = createKeyData();

		this.fighting.processingFight(this.nowFrame, this.keyData);
		this.frameData = this.fighting.createFrameData(this.nowFrame, this.currentRound, this.keyData);
	}

	/** 各ラウンド終了時の処理を行う. */
	private void processingRoundEnd() {
		this.isFinished = true;
		this.currentRound++;
		this.roundStartFlag = true;
	}

	/**
	 * キャラクターが倒されたかどうかを判定する.
	 *
	 * @return true: P1 or P2が倒された; false: otherwise
	 */
	private boolean isBeaten() {
		return FlagSetting.limitHpFlag
				&& (this.frameData.getCharacter(true).getHp() <= 0 || this.frameData.getCharacter(false).getHp() <= 0);
	}

	/**
	 * 1ラウンドの制限時間が経過したかどうかを判定する.<br>
	 *
	 * @return true: 1ラウンドの制限時間が経過した; false: otherwise
	 */
	private boolean isTimeOver() {
		return this.nowFrame == GameSetting.ROUND_FRAME_NUMBER - 1;
	}

	/** 各ラウンド開始時に, 対戦情報や現在のフレームなどの初期化を行う */
	private void initRound() {
		this.fighting.initRound();
		this.nowFrame = 0;
		this.roundStartFlag = false;
		this.elapsedBreakTime = 0;
		this.isFinished = false;
	}

	/**
	 * 対戦処理に用いるP1, P2の入力キーを作成する.<br>
	 *
	 * @return P1, P2の入力キー
	 */
	private KeyData createKeyData() {
		Key[] temp = new Key[2];

		for (int i = 0; i < 2; i++) {
			temp[i] = new Key();
			byte keyByte = 0;

			try {
				this.dis.readBoolean(); // front
				this.dis.readByte(); // remaingFrame
				this.dis.readByte(); // actionOrdinal
				this.dis.readInt(); // hp
				this.dis.readInt(); // energy
				this.dis.readInt(); // x
				this.dis.readInt(); // y
				keyByte = this.dis.readByte();
			} catch (EOFException e) {
				Logger.getAnonymousLogger().log(Level.INFO, "The replay file was finished in the middle");
				try {
					this.dis.close();
					this.isFinished = true;
				} catch (IOException e1) {
					// TODO 自動生成された catch ブロック
					e1.printStackTrace();
				}
				// BGMを止める
				SoundManager.getInstance().stop(SoundManager.getInstance().getBackGroundMusic());
				transitionProcess();

				break;
			} catch (IOException e1) {
				e1.printStackTrace();
			}

			temp[i].U = convertItoB(keyByte / 64);
			keyByte %= 64;
			temp[i].R = convertItoB(keyByte / 32);
			keyByte %= 32;
			temp[i].L = convertItoB(keyByte / 16);
			keyByte %= 16;
			temp[i].D = convertItoB(keyByte / 8);
			keyByte %= 8;
			temp[i].C = convertItoB(keyByte / 4);
			keyByte %= 4;
			temp[i].B = convertItoB(keyByte / 2);
			keyByte %= 2;
			temp[i].A = convertItoB(keyByte / 1);
			keyByte %= 1;
		}

		return new KeyData(temp);
	}

	/**
	 * boolean型変数をint型に変換する
	 *
	 * @param b
	 *            変換したいboolean型の変数
	 *
	 * @return 1 : 引数がtrueのとき, 0: 引数がfalseのとき
	 */
	private boolean convertItoB(int i) {
		return i == 1 ? true : false;
	}

	/** 使用キャラクターや最大HPといったヘッダ情報を読み込む */
	protected void readHeader() {
		for (int i = 0; i < 2; i++) {
			try {
				int checkMode = dis.readInt();

				// Check whether fighting mode is limited HP mode or not
				// If it is HP mode, checkMode is less than 0 (e.g. -1)
				if (checkMode < 0) {
					LaunchSetting.maxHp[i] = dis.readInt();
					LaunchSetting.characterNames[i] = GameSetting.CHARACTERS[dis.readInt()];
					FlagSetting.limitHpFlag = true;
				} else {
					LaunchSetting.characterNames[i] = GameSetting.CHARACTERS[checkMode];
					FlagSetting.limitHpFlag = false;
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	/** Replayの再生速度を更新する. */
	private void updatePlaySpeed() {
		Key key = InputManager.getInstance().getKeyData().getKeys()[0];

		if (key.U) {
			this.playSpeedIndex = ++this.playSpeedIndex % this.playSpeedArray.length;
		}
		if (key.D) {
			this.playSpeedIndex = (--this.playSpeedIndex + this.playSpeedArray.length) % this.playSpeedArray.length;
		}
	}

	private void transitionProcess() {
		HomeMenu homeMenu = new HomeMenu();
		this.setTransitionFlag(true); // 現在のシーンからの遷移要求をtrueに
		this.setNextGameScene(homeMenu); // 次のシーンをセットする
	}

	public FrameData getFrameData() {
		return new FrameData(this.frameData);
	}

	public ScreenData getScreenData() {
		return new ScreenData(this.screenData);
	}

}
