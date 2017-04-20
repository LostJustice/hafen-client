/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static haven.CharWnd.*;
import static haven.Inventory.*;
import static haven.Window.wbox;

public class FightWndEx extends Widget {
    public final int nsave;
    public int maxact;
    public final Actions actlist;
    public final ActionTypes acttypes;
    public final Savelist savelist;
    public List<Action> ALL = new ArrayList<Action>();
    private List<Action> acts = ALL;
    private ActionType selectedType = ActionType.All;
    public final Action[] order;
    public int usesave;
    private final Text[] saves;
    private final CharWnd.LoadingTextBox info;
    private final Label count;
    private boolean needFilter = false;

    public class Action {
	public final Indir<Resource> res;
	private final int id;
	public int a, u;
	private Text rnm, ru;
	private Tex ri;

	public Action(Indir<Resource> res, int id, int a, int u) {
	    this.res = res;
	    this.id = id;
	    this.a = a;
	    this.u = u;
	}

	public String rendertext() {
	    StringBuilder buf = new StringBuilder();
	    Resource res = this.res.get();
	    buf.append("$img[")
		.append(res.name)
		.append("]\n\n")
		.append("$b{$font[serif,16]{")
		.append(res.layer(Resource.tooltip).t)
		.append("}}\n\n");
	    Resource.Pagina pag = res.layer(Resource.pagina);
	    if(pag != null)
		buf.append(pag.text);
	    return (buf.toString());
	}

	private void a(int a) {
	    if(this.a != a) {
		this.a = a;
		this.ru = null;
	    }
	}

	private void u(int u) {
	    if(this.u != u) {
		this.u = u;
		this.ru = null;
		recount();
	    }
	}
    }

    private void recount() {
	int u = 0;
	for (Action act : ALL)
	    u += act.u;
	count.settext(String.format("Used: %d/%d", u, maxact));
	count.setcolor((u > maxact) ? Color.RED : Color.WHITE);
    }

    private static final Tex[] add = {Resource.loadtex("gfx/hud/buttons/addu"),
	Resource.loadtex("gfx/hud/buttons/addd")};
    private static final Tex[] sub = {Resource.loadtex("gfx/hud/buttons/subu"),
	Resource.loadtex("gfx/hud/buttons/subd")};

    public class Actions extends Listbox<Action> implements DTarget {
	private boolean loading = false;
	private int da = -1, ds = -1;
	UI.Grab d = null;
	Action drag = null;
	Coord dp;

	public Actions(int w, int h) {
	    super(w, h, attrf.height() + 2);
	}

	protected Action listitem(int n) {return (acts.get(n));}

	protected int listitems() {return (acts.size());}

	protected void drawbg(GOut g) {}

	protected void drawitem(GOut g, Action act, int idx) {
	    g.chcolor((idx % 2 == 0) ? CharWnd.every : CharWnd.other);
	    g.frect(Coord.z, g.sz);
	    g.chcolor();
	    if(act.ru == null) act.ru = attrf.render(String.format("%d/%d", act.u, act.a));
	    try {
		if(act.ri == null)
		    act.ri = new TexI(PUtils.convolvedown(act.res.get().layer(Resource.imgc).img, new Coord(itemh, itemh), CharWnd.iconfilter));
		g.image(act.ri, Coord.z);
	    } catch (Loading l) {
		g.image(WItem.missing.layer(Resource.imgc).tex(), Coord.z, new Coord(itemh, itemh));
	    }
	    int ty = (itemh - act.rnm.sz().y) / 2;
	    g.image(act.rnm.tex(), new Coord(itemh + 2, ty));
	    g.aimage(act.ru.tex(), new Coord(sz.x - 45, ty), 1.0, 0.0);
	    g.aimage(add[da == idx ? 1 : 0], new Coord(sz.x - 10, itemh / 2), 1.0, 0.5);
	    g.aimage(sub[ds == idx ? 1 : 0], new Coord(sz.x - 25, itemh / 2), 1.0, 0.5);
	}

	public void change(final Action act) {
	    if(act != null)
		info.settext(new Indir<String>() {
		    public String get() {return (act.rendertext());}
		});
	    else if(sel != null)
		info.settext("");
	    super.change(act);
	}

	public boolean mousewheel(Coord c, int am) {
	    if(ui.modshift) {
		Action act = itemat(c);
		if(act != null)
		    setu(act, act.u - am);
		return (true);
	    }
	    return (super.mousewheel(c, am));
	}

	public void draw(GOut g) {
	    if(loading) {
		loading = false;
		for (Action act : acts) {
		    try {
			act.rnm = attrf.render(act.res.get().layer(Resource.tooltip).t);
		    } catch (Loading l) {
			act.rnm = attrf.render("...");
			loading = true;
		    }
		}
		acts.sort((a, b) -> (a.rnm.text.compareTo(b.rnm.text)));
	    }
	    if((drag != null) && (dp == null)) {
		try {
		    final Tex dt = drag.res.get().layer(Resource.imgc).tex();
		    ui.drawafter(new UI.AfterDraw() {
			public void draw(GOut g) {
			    g.image(dt, ui.mc.add(dt.sz().div(2).inv()));
			}
		    });
		} catch (Loading l) {}
	    }
	    super.draw(g);
	}

	private boolean onadd(Coord c, int idx) {
	    Coord ic = c.sub(0, (idx - sb.val) * itemh);
	    int by = (itemh - add[0].sz().y) / 2;
	    return (ic.isect(new Coord(sz.x - 10 - add[0].sz().x, by), add[0].sz()));
	}

	private boolean onsub(Coord c, int idx) {
	    Coord ic = c.sub(0, (idx - sb.val) * itemh);
	    int by = (itemh - sub[0].sz().y) / 2;
	    return (ic.isect(new Coord(sz.x - 25 - add[0].sz().x, by), add[0].sz()));
	}

	public void drag(Action act) {
	    if(d == null)
		d = ui.grabmouse(this);
	    drag = act;
	    dp = null;
	}

	public boolean mousedown(Coord c, int button) {
	    if(button == 1) {
		int idx = (c.y / itemh) + sb.val;
		if(idx < listitems()) {
		    if(onadd(c, idx)) {
			da = idx;
			d = ui.grabmouse(this);
			return (true);
		    } else if(onsub(c, idx)) {
			ds = idx;
			d = ui.grabmouse(this);
			return (true);
		    }
		}
		super.mousedown(c, button);
		if((sel != null) && (c.x < sb.c.x)) {
		    d = ui.grabmouse(this);
		    drag = sel;
		    dp = c;
		}
		return (true);
	    }
	    return (super.mousedown(c, button));
	}

	public void mousemove(Coord c) {
	    super.mousemove(c);
	    if((drag != null) && (dp != null)) {
		if(c.dist(dp) > 5)
		    dp = null;
	    }
	}

	private boolean setu(Action act, int u) {
	    u = Utils.clip(u, 0, act.a);
	    int s;
	    for (s = 0; s < order.length; s++) {
		if(order[s] == act)
		    break;
	    }
	    if(u > 0) {
		if(s == order.length) {
		    for (s = 0; s < order.length; s++) {
			if(order[s] == null)
			    break;
		    }
		    if(s == order.length)
			return (false);
		    order[s] = act;
		}
	    } else {
		if(s < order.length)
		    order[s] = null;
	    }
	    act.u(u);
	    return (true);
	}

	public boolean mouseup(Coord c, int button) {
	    if((d != null) && (button == 1)) {
		d.remove();
		d = null;
		if(drag != null) {
		    if(dp == null)
			ui.dropthing(ui.root, c.add(rootpos()), drag);
		    drag = null;
		}
		if(da >= 0) {
		    if(onadd(c, da)) {
			Action act = listitem(da);
			setu(act, act.u + 1);
		    }
		    da = -1;
		} else if(ds >= 0) {
		    if(onsub(c, ds)) {
			Action act = listitem(ds);
			setu(act, act.u - 1);
		    }
		    ds = -1;
		}
		return (true);
	    }
	    return (super.mouseup(c, button));
	}

	public boolean drop(Coord cc, Coord ul) {
	    return (false);
	}

	public boolean iteminteract(Coord cc, Coord ul) {
	    Action act = itemat(cc);
	    if(act != null)
		FightWndEx.this.wdgmsg("itemact", act.id, ui.modflags());
	    return (true);
	}
    }

    public int findorder(Action a) {
	for (int i = 0; i < order.length; i++) {
	    if(order[i] == a)
		return (i);
	}
	return (-1);
    }

    public static final String[] keys = {"1", "2", "3", "4", "5", "\u21e71", "\u21e72", "\u21e73", "\u21e74", "\u21e75"};

    public class BView extends Widget implements DropTarget {
	private UI.Grab grab;
	private Action drag;
	private Coord dp;
	private final Coord[] animoff = new Coord[order.length];
	private final double[] animpr = new double[order.length];
	private boolean anim = false;

	private BView() {
	    super(new Coord(((invsq.sz().x + 2) * (order.length - 1)) + (10 * ((order.length - 1) / 5)), 0).add(invsq.sz()));
	}

	private Coord itemc(int i) {
	    return (new Coord(((invsq.sz().x + 2) * i) + (10 * (i / 5)), 0));
	}

	private int citem(Coord c) {
	    for (int i = 0; i < order.length; i++) {
		if(c.isect(itemc(i), invsq.sz()))
		    return (i);
	    }
	    return (-1);
	}

	final Tex[] keys = new Tex[10];

	{
	    for (int i = 0; i < 10; i++)
		this.keys[i] = Text.render(FightWndEx.keys[i]).tex();
	}

	public void draw(GOut g) {
	    int[] reo = null;
	    if(anim) {
		reo = new int[order.length];
		for (int i = 0, a = 0, b = order.length - 1; i < order.length; i++) {
		    if(animoff[i] == null)
			reo[a++] = i;
		    else
			reo[b--] = i;
		}
	    }
	    for (int io = 0; io < order.length; io++) {
		int i = (reo == null) ? io : reo[io];
		Coord c = itemc(i);
		g.image(invsq, c);
		Action act = order[i];
		try {
		    if(act != null) {
			Coord ic = c.add(1, 1);
			if(animoff[i] != null) {
			    ic = ic.add(animoff[i].mul(Math.pow(1.0 - animpr[i], 3)));
			}
			g.image(act.res.get().layer(Resource.imgc).tex(), ic);
		    }
		} catch (Loading l) {}
		g.chcolor(156, 180, 158, 255);
		g.aimage(keys[i], c.add(invsq.sz().sub(2, 0)), 1, 1);
		g.chcolor();
	    }
	}

	public boolean mousedown(Coord c, int button) {
	    if(button == 1) {
		int s = citem(c);
		if(s >= 0) {
		    Action act = order[s];
		    actlist.change(act);
		    actlist.display();
		    if(act != null) {
			grab = ui.grabmouse(this);
			drag = act;
			dp = c;
		    }
		    return (true);
		}
	    } else if(button == 3) {
		int s = citem(c);
		if(s >= 0) {
		    if(order[s] != null)
			order[s].u(0);
		    order[s] = null;
		    return (true);
		}
	    }
	    return (super.mousedown(c, button));
	}

	public void mousemove(Coord c) {
	    super.mousemove(c);
	    if(dp != null) {
		if(c.dist(dp) > 5) {
		    grab.remove();
		    actlist.drag(drag);
		    grab = null;
		    drag = null;
		    dp = null;
		}
	    }
	}

	public boolean mouseup(Coord c, int button) {
	    if(grab != null) {
		grab.remove();
		grab = null;
		drag = null;
		dp = null;
	    }
	    return (super.mouseup(c, button));
	}

	private void animate(int s, Coord off) {
	    animoff[s] = off;
	    animpr[s] = 0.0;
	    anim = true;
	}

	public boolean dropthing(Coord c, Object thing) {
	    if(thing instanceof Action) {
		Action act = (Action) thing;
		int s = citem(c);
		if(s < 0)
		    return (false);
		if(order[s] != act) {
		    if(order[s] != null) {
			int cp = findorder(act);
			if(cp >= 0) {
			    order[cp] = order[s];
			    animate(cp, itemc(s).sub(itemc(cp)));
			} else {
			    order[s].u(0);
			}
		    }
		    order[s] = act;
		    if(act.u < 1)
			act.u(1);
		}
		return (true);
	    }
	    return (false);
	}

	public void tick(double dt) {
	    if(anim) {
		boolean na = false;
		for (int i = 0; i < order.length; i++) {
		    if(animoff[i] != null) {
			if((animpr[i] += (dt * 3)) > 1.0)
			    animoff[i] = null;
			else
			    na = true;
		    }
		}
		anim = na;
	    }
	}
    }

    public class Savelist extends Dropbox<Integer> {
	public Savelist(int w, int h) {
	    super(w, h, attrf.height() + 2);
	    setcanfocus(true);
	    sel = 0;
	}

	protected Integer listitem(int idx) {return (idx);}

	protected int listitems() {return (nsave);}

	protected void drawbg(GOut g) {}

	protected void drawitem(GOut g, Integer save, int n) {
	    g.aimage(saves[save].tex(), new Coord(3, itemh / 2), 0.0, 0.5);
	}

	public void change(Integer sel) {
	    super.change(sel);
	    load(sel);
	    use(sel);
	}

	public void change2(Integer sel) {
	    super.change(sel);
	}
    }

    public void load(int n) {
	wdgmsg("load", n);
    }

    public void save(int n) {
	List<Object> args = new LinkedList<Object>();
	args.add(n);
	if(saves[n] != unused)
	    args.add(saves[n].text);
	for (int i = 0; i < order.length; i++) {
	    if(order[i] == null) {
		args.add(null);
	    } else {
		args.add(order[i].id);
		args.add(order[i].u);
	    }
	}
	wdgmsg("save", args.toArray(new Object[0]));
    }

    public void use(int n) {
	wdgmsg("use", n);
    }

    private Text unused = new Text.Foundry(attrf.font.deriveFont(java.awt.Font.ITALIC)).aa(true).render("Unused save");

    public FightWndEx(int nsave, int nact, int max) {
	super(Coord.z);
	this.nsave = nsave;
	this.maxact = max;
	this.order = new Action[nact];
	this.saves = new Text[nsave];
	for (int i = 0; i < nsave; i++)
	    saves[i] = unused;

	Widget p;
	info = add(new CharWnd.LoadingTextBox(new Coord(223, 177), "", CharWnd.ifnd), new Coord(5, 35).add(wbox.btloff()));
	info.bg = new Color(0, 0, 0, 128);
	Frame.around(this, Collections.singletonList(info));

	acttypes = add(new ActionTypes(this::actionTypeSelected), wbox.btloff().add(245, 32));
	acttypes.setSelectedColor(new Color(122, 191, 86, 153));
	acttypes.select(0);

	add(new Img(CharWnd.catf.render("Martial Arts & Combat Schools").tex()), 0, 0);
	actlist = add(new Actions(250, 7), new Coord(245, 58).add(wbox.btloff()));
	Frame.around(this, Collections.singletonList(actlist));

	p = add(new BView(), 5, 223);
	count = add(new Label(""), p.c.add(p.sz.x + 10, 0));

	int y = 260;
	savelist = add(new Savelist(370, 5), new Coord(5, y).add(wbox.btloff()));
	add(new Button(110, "Save", false) {
	    public void click() {
		if(savelist.sel == null || savelist.sel < 0) {
		    getparent(GameUI.class).error("No save entry selected.");
		} else {
		    save(savelist.sel);
		    use(savelist.sel);
		}
	    }
	}, 395, y);
	pack();
    }

    private Void actionTypeSelected(TabStrip.Button<ActionType> button) {
	selectedType = button.tag;
	needFilter = true;
	return null;
    }


    @Override
    public void tick(double dt) {
	super.tick(dt);
	if(needFilter) {
	    doFilter();
	}
    }

    private void doFilter() {
	try {
	    if(ALL != null) {
		acts = ALL.stream().filter(selectedType::matches).collect(Collectors.toList());
		acts.sort((a, b) -> (a.res.get().layer(Resource.tooltip).t.compareTo(b.res.get().layer(Resource.tooltip).t)));
		actlist.change(actlist.listitems() > 0 ? actlist.listitem(0) : null);
		needFilter = false;
	    }
	} catch (Resource.Loading ignored) {
	}
    }

    public Action findact(int resid) {
	for (Action act : ALL) {
	    if(act.id == resid)
		return (act);
	}
	return (null);
    }

    public void uimsg(String nm, Object... args) {
	if(Objects.equals(nm, "avail")) {
	    List<Action> acts = new ArrayList<Action>();
	    int a = 0;
	    while (true) {
		int resid = (Integer) args[a++];
		if(resid < 0)
		    break;
		int av = (Integer) args[a++];
		Action pact = findact(resid);
		if(pact == null) {
		    acts.add(new Action(ui.sess.getres(resid), resid, av, 0));
		} else {
		    acts.add(pact);
		    pact.a(av);
		}
	    }
	    this.ALL = acts;
	    actlist.loading = true;
	    needFilter = true;
	} else if(Objects.equals(nm, "used")) {
	    int a = 0;
	    for (Action act : acts)
		act.u(0);
	    for (int i = 0; i < order.length; i++) {
		int resid = (Integer) args[a++];
		if(resid < 0) {
		    order[i] = null;
		    continue;
		}
		int us = (Integer) args[a++];
		(order[i] = findact(resid)).u(us);
	    }
	} else if(Objects.equals(nm, "saved")) {
	    int fl = (Integer) args[0];
	    for (int i = 0; i < nsave; i++) {
		if((fl & (1 << i)) != 0) {
		    if(args[i + 1] instanceof String)
			saves[i] = attrf.render((String) args[i + 1]);
		    else
			saves[i] = attrf.render(String.format("Saved school %d", i + 1));
		} else {
		    saves[i] = unused;
		}
	    }
	} else if(Objects.equals(nm, "use")) {
	    usesave = (Integer) args[0];
	    savelist.change2(Integer.valueOf(usesave));
	} else if(Objects.equals(nm, "max")) {
	    maxact = (Integer) args[0];
	    recount();
	} else {
	    super.uimsg(nm, args);
	}
    }

    class ActionTypes extends TabStrip<ActionType> {
	private final Function<Button<ActionType>, Void> selected;

	ActionTypes(Function<Button<ActionType>, Void> selected) {
	    super();
	    this.selected = selected;
	    ActionType[] types = ActionType.values();
	    for (int i = 0; i < types.length; i++) {
		insert(i, types[i].icon(), "", types[i].name()).tag = types[i];
	    }
	}

	@Override
	protected void selected(Button<ActionType> button) {
	    if(selected != null) {
		selected.apply(button);
	    }
	}
    }

    enum ActionType {
	All("paginae/act/pow", null),
	Attacks("paginae/act/atk", new HashSet<>(Arrays.asList(
	    "paginae/atk/pow",
	    "paginae/atk/lefthook",
	    "paginae/atk/lowblow",
	    "paginae/atk/oppknock",
	    "paginae/atk/ripapart",
	    "paginae/atk/fullcircle",
	    "paginae/atk/cleave",
	    "paginae/atk/barrage",
	    "paginae/atk/sideswipe",
	    "paginae/atk/sting",
	    "paginae/atk/sos",
	    "paginae/atk/knockteeth",
	    "paginae/atk/kick",
	    "paginae/atk/haymaker",
	    "paginae/atk/chop",
	    "paginae/atk/gojug",
	    "paginae/atk/uppercut",
	    "paginae/atk/punchboth",
	    "paginae/atk/stealthunder",
	    "paginae/atk/ravenbite",
	    "paginae/atk/takedown"
	))),
	Restorations("paginae/craft/food", new HashSet<>(Arrays.asList(
	    "paginae/atk/regain",
	    "paginae/atk/dash",
	    "paginae/atk/zigzag",
	    "paginae/atk/yieldground",
	    "paginae/atk/watchmoves",
	    "paginae/atk/sidestep",
	    "paginae/atk/qdodge",
	    "paginae/atk/jump",
	    "paginae/atk/fdodge",
	    "paginae/atk/artevade",
	    "paginae/atk/flex"
	))),
	Maneuvers("paginae/craft/curios", new HashSet<>(Arrays.asList(
	    "paginae/atk/toarms",
	    "paginae/atk/shield",
	    "paginae/atk/parry",
	    "paginae/atk/oakstance",
	    "paginae/atk/dorg",
	    "paginae/atk/chinup",
	    "paginae/atk/bloodlust",
	    "paginae/atk/combmed"
	))),
	Moves("paginae/craft/stoneaxe", new HashSet<>(Arrays.asList(
	    "paginae/atk/think",
	    "paginae/atk/takeaim"
	)));

	private final String res;
	private final Set<String> list;
	private Tex icon;

	Tex icon() {
	    if(icon == null) {
		icon = new TexI(PUtils.convolvedown(Resource.loadimg(res), new Coord(20, 20), CharWnd.iconfilter));
	    }
	    return icon;
	}

	boolean matches(Action action) {
	    return list == null || list.contains(action.res.get().name);
	}

	ActionType(String res, Set<String> list) {
	    this.res = res;
	    this.list = list;
	}
    }
}
