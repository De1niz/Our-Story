package com.ourstory;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.system.AppSettings;
import java.util.ArrayList;
import java.util.List;

/** Our Story v2: two-player character creator + single-player foundation. All in-game text is English. */
public class Main extends SimpleApplication implements ActionListener {
    private enum Screen { SELECT, HUB, CHAPTER_ONE, MONTH }
    private static final ColorRGBA SKY = new ColorRGBA(.58f, .72f, .83f, 1f);
    private static final ColorRGBA STUDIO = new ColorRGBA(.70f, .77f, .86f, 1f);

    private Screen screen=Screen.SELECT;
    private final Node environment=new Node("Environment");
    private static final float GAME_SCALE = 1.2f; // makes both characters a bit taller in the world
    private final Node avatarRoot=new Node("Players");
    private final Node[] avatars={new Node("Player 1"),new Node("Player 2")};
    private final Node creatorStage=new Node("Character Creator Preview");
    private CharacterCreatorScreen creator;
    private BulletAppState bullet;
    private CoopPlayers coop;
    private boolean firstEntry, pendingHub, enlarged;
    private float resizeWait;
    private int prevWidth;
    private static final float BASE_W=1280f, BASE_H=720f;
    private LoadingScreen loading;
    private GameManager games;
    private WorldBuilder builder;
    private HubScene hub;
    private ChapterOneScene chapter;
    private BitmapText title,subtitle,story,progress,instructions;
    private Geometry topPanel,bottomPanel;
    private int month=2;
    private final boolean[] visited=new boolean[3];
    private DirectionalLight sun;

    public static void main(String[] args) {
        Main app=new Main();
        AppSettings settings=new AppSettings(true);
        settings.setTitle("Our Story — Character Creator v2");
        settings.setResolution(1280,720);
        settings.setVSync(true);
        settings.setSamples(4);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start();
    }

    @Override public void simpleInitApp() {
        setDisplayFps(false); setDisplayStatView(false);
        flyCam.setEnabled(false);
        // ESC must not close the game while players are using it to reset their zoom.
        if (inputManager.hasMapping(INPUT_MAPPING_EXIT)) inputManager.deleteMapping(INPUT_MAPPING_EXIT);
        rootNode.attachChild(environment);
        rootNode.attachChild(avatarRoot);
        avatarRoot.attachChild(avatars[0]);
        avatarRoot.attachChild(avatars[1]);
        rootNode.attachChild(creatorStage);
        viewPort.setBackgroundColor(SKY);
        AmbientLight ambient=new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(.55f));
        rootNode.addLight(ambient);
        sun=new DirectionalLight();
        sun.setDirection(new Vector3f(-.45f,-1f,-.35f).normalizeLocal());
        sun.setColor(new ColorRGBA(1f,.92f,.79f,1).mult(1.3f));
        rootNode.addLight(sun);
        DirectionalLightShadowRenderer shadows=new DirectionalLightShadowRenderer(assetManager,2048,3);
        shadows.setLight(sun);
        shadows.setShadowIntensity(.55f);
        viewPort.addProcessor(shadows);
        bullet=new BulletAppState();
        stateManager.attach(bullet);
        builder=new WorldBuilder(assetManager,bullet.getPhysicsSpace(),environment);
        hub=new HubScene(builder);
        chapter=new ChapterOneScene(builder);
        creator=new CharacterCreatorScreen(this,creatorStage,sun);
        coop=new CoopPlayers(bullet.getPhysicsSpace(),avatars,cam,inputManager);
        loading=new LoadingScreen(this);
        games=new GameManager(this,environment,coop,inputManager,(t,sub,help)->{
            if(t==null) {
                title.setText("THE MEMORY GARDEN"); subtitle.setText(HUB_HINT);
                instructions.setText(HUB_CONTROLS);
            } else { title.setText(t); subtitle.setText(sub); instructions.setText(help); }
        });
        hub.setGames(games);
        createAvatars();
        inputManager.addMapping("Interact",new KeyTrigger(KeyInput.KEY_E),new KeyTrigger(KeyInput.KEY_RETURN));
        inputManager.addMapping("Back",new KeyTrigger(KeyInput.KEY_BACK));
        inputManager.addListener(this,"Interact","Back");
        // Player 1: W/S choose the part, A/D change it, Q done (zoom out), R ready.
        // Player 2: Up/Down choose the part, Left/Right change it, Right Shift done, Enter ready.
        inputManager.addMapping("P1CatPrev",new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("P1CatNext",new KeyTrigger(KeyInput.KEY_S));
        inputManager.addMapping("P1OptPrev",new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("P1OptNext",new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping("P1Done",new KeyTrigger(KeyInput.KEY_Q));
        inputManager.addMapping("P1Ready",new KeyTrigger(KeyInput.KEY_R));
        inputManager.addMapping("P2CatPrev",new KeyTrigger(KeyInput.KEY_UP));
        inputManager.addMapping("P2CatNext",new KeyTrigger(KeyInput.KEY_DOWN));
        inputManager.addMapping("P2OptPrev",new KeyTrigger(KeyInput.KEY_LEFT));
        inputManager.addMapping("P2OptNext",new KeyTrigger(KeyInput.KEY_RIGHT));
        inputManager.addMapping("P2Done",new KeyTrigger(KeyInput.KEY_RSHIFT));
        inputManager.addMapping("P2Ready",new KeyTrigger(KeyInput.KEY_RETURN));
        inputManager.addMapping("CreatorClick",new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping("EscapeKey",new KeyTrigger(KeyInput.KEY_ESCAPE));
        inputManager.addMapping("CreatorStart",new KeyTrigger(KeyInput.KEY_F));
        inputManager.addListener(this,"P1CatPrev","P1CatNext","P1OptPrev","P1OptNext","P1Done","P1Ready",
                "P2CatPrev","P2CatNext","P2OptPrev","P2OptNext","P2Done","P2Ready",
                "CreatorClick","EscapeKey","CreatorStart");
        topPanel=addHudPanel("Top readable panel",cam.getWidth()/2f,cam.getHeight()-57f,
                cam.getWidth()/2f,57f);
        bottomPanel=addHudPanel("Bottom readable panel",cam.getWidth()/2f,68f,
                cam.getWidth()/2f,68f);
        title=ui(26,cam.getHeight()-28,29);
        subtitle=ui(26,cam.getHeight()-69,18);
        progress=ui(26,120,18);
        story=ui(26,89,16);
        instructions=ui(26,47,16);
        showSelection();
    }

    // Opaque, light UI panels keep black text readable on every background.
    private Geometry addHudPanel(String name,float x,float y,float hx,float hy) {
        Geometry panel=new Geometry(name,new Box(hx,hy,.001f));
        Material mat=new Material(assetManager,"Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color",new ColorRGBA(.97f,.96f,.93f,.86f));
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        panel.setMaterial(mat);
        panel.setLocalTranslation(x,y,-1f);
        guiNode.attachChild(panel);
        return panel;
    }

    private BitmapText ui(int x,int y,int fontSize) {
        BitmapText text=new BitmapText(guiFont);
        text.setSize(fontSize);
        text.setColor(ColorRGBA.Black);
        text.setLocalTranslation(x,y,0);
        guiNode.attachChild(text);
        return text;
    }

    /** Builds both models from the character creator choices. */
    private void createAvatars() {
        CharacterModelBuilder models=new CharacterModelBuilder(assetManager);
        ColorRGBA[] markerColors={new ColorRGBA(.22f,.48f,.92f,1f),new ColorRGBA(.93f,.33f,.55f,1f)};
        for(int p=0;p<2;p++) {
            avatars[p].detachAllChildren();
            Node model=models.build(creator.get(p));
            model.scale(GAME_SCALE);
            WalkAnimator walk=new WalkAnimator(model);
            model.addControl(walk);
            coop.setAnimator(p,walk);
            avatars[p].attachChild(model);
            // small floating diamond so you can tell who is who
            Geometry marker=new Geometry("Player marker",new Sphere(8,12,1f));
            Material mat=new Material(assetManager,"Common/MatDefs/Misc/Unshaded.j3md");
            mat.setColor("Color",markerColors[p]);
            marker.setMaterial(mat);
            marker.setLocalScale(.13f,.21f,.13f);
            marker.setLocalTranslation(0,2.85f*CharacterModelBuilder.heightScale(creator.get(p)),0);
            avatars[p].attachChild(marker);
        }
    }

    private void loadWorld() {
        builder.clear();
        coop.setEnabled(false);
        progress.setText(""); story.setText("");
    }

    private void showSelection() {
        screen=Screen.SELECT;
        loadWorld();
        avatarRoot.setCullHint(Spatial.CullHint.Always);
        // The creator draws its own studio and HUD; hide the game's text panels.
        topPanel.setCullHint(Spatial.CullHint.Always);
        bottomPanel.setCullHint(Spatial.CullHint.Always);
        title.setText(""); subtitle.setText(""); instructions.setText("");
        viewPort.setBackgroundColor(STUDIO);
        creator.show();
        inputManager.setCursorVisible(true);
    }

    private void leaveSelection() {
        creator.hide();
        topPanel.setCullHint(Spatial.CullHint.Inherit);
        bottomPanel.setCullHint(Spatial.CullHint.Inherit);
        viewPort.setBackgroundColor(SKY);
    }

    /** Character selection is finished: make the window big, then show the loading screen. */
    private void startAdventure() {
        creator.hide();
        viewPort.setBackgroundColor(SKY);
        hudVisible(false);
        screen=Screen.HUB;
        firstEntry=true;
        coop.setEnabled(false);
        prevWidth=cam.getWidth();
        resizeWait=0f;
        pendingHub=true;
        if(enlarged) resizeWait=3f; else { enlarged=true; enlargeWindow(); }
    }

    /** Uses the whole screen for the game area so every corner of the garden is easy to see. */
    private void enlargeWindow() {
        int w=0,h=0;
        try {
            Class<?> glfw=Class.forName("org.lwjgl.glfw.GLFW");
            long monitor=(Long)glfw.getMethod("glfwGetPrimaryMonitor").invoke(null);
            Object mode=glfw.getMethod("glfwGetVideoMode",long.class).invoke(null,monitor);
            w=(Integer)mode.getClass().getMethod("width").invoke(mode);
            h=(Integer)mode.getClass().getMethod("height").invoke(mode);
        } catch(Throwable ignored) { }
        AppSettings s=new AppSettings(false);
        s.copyFrom(context.getSettings());
        if(w>0&&h>0) { s.setResolution(w,h); s.setFullscreen(true); }
        else { s.setResolution(1600,900); s.setFullscreen(false); }
        setSettings(s);
        restart();
    }

    /** Re-positions the text panels after the window size changed. */
    private void relayoutHud() {
        float w=cam.getWidth(),h=cam.getHeight(),k=h/BASE_H;
        topPanel.setLocalScale(w/BASE_W,k,1f);
        topPanel.setLocalTranslation(w/2f,h-57f*k,-1f);
        bottomPanel.setLocalScale(w/BASE_W,k,1f);
        bottomPanel.setLocalTranslation(w/2f,68f*k,-1f);
        place(title,26,h-28*k,29,k); place(subtitle,26,h-69*k,18,k); place(progress,26,120*k,18,k);
        place(story,26,89*k,16,k); place(instructions,26,47*k,16,k);
        cam.setFrustumPerspective(45f,w/h,.1f,400f);
    }

    private void place(BitmapText t,float x,float y,int size,float k) {
        t.setSize(size*k);
        t.setLocalTranslation(x*k,y,0f);
    }

    private void hudVisible(boolean on) {
        Spatial.CullHint hint=on ? Spatial.CullHint.Inherit : Spatial.CullHint.Always;
        topPanel.setCullHint(hint); bottomPanel.setCullHint(hint);
        title.setCullHint(hint); subtitle.setCullHint(hint); story.setCullHint(hint);
        progress.setCullHint(hint); instructions.setCullHint(hint);
    }

    private void showHub() {
        boolean fromSelect=firstEntry;
        firstEntry=false;
        screen=Screen.HUB;
        coop.setEnabled(false);
        hudVisible(false);
        List<LoadingScreen.Step> steps=new ArrayList<>();
        steps.add(new LoadingScreen.Step("Dressing your characters...",()->createAvatars()));
        steps.add(new LoadingScreen.Step("Growing the Memory Garden...",()->{ loadWorld(); hub.build(); }));
        steps.add(new LoadingScreen.Step("Setting up the game tables...",()->{}));
        steps.add(new LoadingScreen.Step("Lighting the lanterns...",()->{}));
        loading.begin("OUR STORY",steps,fromSelect ? 4.2f : 1.8f,()->{
            avatarRoot.setCullHint(Spatial.CullHint.Inherit);
            startCoop(hub.spawn(0),hub.spawn(1));
            hudVisible(true);
            title.setText("THE MEMORY GARDEN");
            subtitle.setText(HUB_HINT);
            instructions.setText(HUB_CONTROLS);
        });
    }

    private static final String HUB_CONTROLS=CONTROLS_TEXT()+" | E / ENTER visit or play | BACKSPACE back";
    private static String CONTROLS_TEXT() { return "P1: WASD + SPACE  |  P2: ARROWS + RIGHT SHIFT  |  MOUSE: camera"; }
    private static final String HUB_HINT="Walk to a glowing lantern - one for each month of our story.";
    private static final String CONTROLS="P1: WASD + SPACE  |  P2: ARROWS + RIGHT SHIFT  |  MOUSE: camera";

    private void startCoop(Vector3f a,Vector3f b) {
        coop.teleport(0,a,0f);
        coop.teleport(1,b,0f);
        coop.resetCamera(0f);
        coop.setEnabled(true);
    }

    private int nearestOf(int a,int b) { return a>=0 ? a : b; }

    private void showChapter() {
        screen=Screen.CHAPTER_ONE;
        coop.setEnabled(false);
        hudVisible(false);
        List<LoadingScreen.Step> steps=new ArrayList<>();
        steps.add(new LoadingScreen.Step("Opening the memory...",()->{ loadWorld(); chapter.build(); }));
        steps.add(new LoadingScreen.Step("Almost there...",()->{}));
        loading.begin("CHAPTER 1",steps,1.6f,()->{
            startCoop(new Vector3f(-1.6f,.12f,27),new Vector3f(1.6f,.12f,27));
            hudVisible(true);
            title.setText("CHAPTER 1 - THE BEGINNING OF US");
            subtitle.setText(ChapterOneContent.CHAPTER_INTRO);
            story.setText("Explore all three memory markers.");
            updateProgress();
            instructions.setText(CONTROLS+" | E / ENTER memory | BACKSPACE garden");
        });
    }

    private void showMonth(int index) {
        screen=Screen.MONTH; month=index;
        coop.setEnabled(false);
        hudVisible(false);
        List<LoadingScreen.Step> steps=new ArrayList<>();
        steps.add(new LoadingScreen.Step("Building the memory world...",()->{
            loadWorld();
            builder.ground(); builder.path(0,28);
            for(int z=-25;z<=25;z+=10){ builder.tree(-9,z);builder.tree(9,z); }
            builder.block("Stone memory monument",0,1,-12,2,1,2,builder.texture("stone.png"),true);
        }));
        steps.add(new LoadingScreen.Step("Almost there...",()->{}));
        loading.begin("MONTH "+index,steps,1.4f,()->{
            startCoop(new Vector3f(-1.6f,.12f,23),new Vector3f(1.6f,.12f,23));
            hudVisible(true);
            title.setText("MONTH "+index+" - MEMORY WORLD");
            subtitle.setText("This memory world is ready for your future chapter.");
            story.setText("Your story will be added here later.");
            instructions.setText(CONTROLS+" | BACKSPACE garden");
        });
    }

    private void updateProgress() {
        int count=0;
        for(boolean value:visited) if(value) count++;
        progress.setText("MEMORIES DISCOVERED: "+count+" / 3");
        if (count==3) story.setText(ChapterOneContent.CHAPTER_END);
    }

    @Override public void simpleUpdate(float tpf) {
        if(pendingHub) {
            resizeWait+=tpf;
            if(cam.getWidth()!=prevWidth||resizeWait>2.5f) {
                pendingHub=false;
                relayoutHud();
                showHub();
            }
            return;
        }
        loading.update(tpf);
        if(loading.isBlocking()) return;
        if(screen==Screen.SELECT) { creator.update(tpf,inputManager.getCursorPosition()); return; }
        coop.update(tpf);
        if(screen==Screen.HUB) {
            hub.sky().follow(cam.getLocation());
            games.update(tpf);
            if(!games.isPlaying()) {
                MiniGame table=games.nearest();
                int near=nearestOf(hub.nearby(coop.getFeet(0)),hub.nearby(coop.getFeet(1)));
                subtitle.setText(table!=null ? "Press E or ENTER to play "+table.name.toUpperCase()
                        : near<0 ? HUB_HINT : "MONTH "+(near+1)+" - Press E or ENTER to visit this memory.");
            }
        } else if(screen==Screen.CHAPTER_ONE) {
            if(chapter.sky()!=null) chapter.sky().follow(cam.getLocation());
            int near=nearestOf(chapter.nearby(coop.getFeet(0)),chapter.nearby(coop.getFeet(1)));
            instructions.setText(near<0 ? CONTROLS+" | E / ENTER memory | BACKSPACE garden" :
                    "Press E or ENTER: "+ChapterOneContent.TITLES[near]+" | BACKSPACE garden");
        }
    }

    @Override public void onAction(String name,boolean pressed,float tpf) {
        if(loading.isBlocking()) return;
        if(screen==Screen.SELECT && name.equals("CreatorClick")) {
            if(pressed) creator.mouseDown(inputManager.getCursorPosition());
            else creator.mouseUp(inputManager.getCursorPosition());
            return;
        }
        if(!pressed) return;
        if(name.equals("EscapeKey")) {
            if(screen==Screen.SELECT) creator.resetViews();
            else if(games.isPlaying()) games.stop();
            else stop();
            return;
        }
        if(screen==Screen.SELECT) {
            switch(name) {
                case "P1CatPrev": creator.changeCategory(0,-1); break;
                case "P1CatNext": creator.changeCategory(0,1); break;
                case "P1OptPrev": creator.changeOption(0,-1); break;
                case "P1OptNext": creator.changeOption(0,1); break;
                case "P1Done": creator.done(0); break;
                case "P1Ready": creator.toggleReady(0); break;
                case "P2CatPrev": creator.changeCategory(1,-1); break;
                case "P2CatNext": creator.changeCategory(1,1); break;
                case "P2OptPrev": creator.changeOption(1,-1); break;
                case "P2OptNext": creator.changeOption(1,1); break;
                case "P2Done": creator.done(1); break;
                case "P2Ready": creator.toggleReady(1); break;
                case "CreatorStart": if(creator.bothReady()) startAdventure(); break;
                default: break;
            }
            return;
        }
        if(name.equals("Back")) {
            if(screen==Screen.HUB&&games.isPlaying()) games.stop();
            else if(screen==Screen.HUB) showSelection();
            else showHub();
        } else if(name.equals("Interact")&&screen==Screen.HUB) {
            if(games.isPlaying()) return;
            MiniGame table=games.nearest();
            if(table!=null) { games.start(table); return; }
            int index=nearestOf(hub.nearby(coop.getFeet(0)),hub.nearby(coop.getFeet(1)));
            if(index==0) showChapter(); else if(index>0) showMonth(index+1);
        } else if(name.equals("Interact")&&screen==Screen.CHAPTER_ONE) {
            int i=nearestOf(chapter.nearby(coop.getFeet(0)),chapter.nearby(coop.getFeet(1)));
            if(i>=0) {
                visited[i]=true;
                subtitle.setText(ChapterOneContent.TITLES[i]+" | "+ChapterOneContent.PLACES[i]);
                story.setText(ChapterOneContent.MEMORIES[i]);
                updateProgress();
            }
        }
    }
}
