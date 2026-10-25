/** Desktop/mobile layout checks against the standalone mock preview. No league server. */
import assert from "node:assert/strict";
import {createRequire} from "node:module";
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||"playwright");
const browser=await chromium.launch({headless:true});
const preview=new URL("../target/draft-layout-preview.html",import.meta.url).href;
try {
  for(const width of [1440,390]) {
    const context=await browser.newContext({viewport:{width,height:1000}});
    const page=await context.newPage(),errors=[];
    page.on("pageerror",error=>errors.push(error.message));
    await page.route("https://**/*",route=>route.fulfill({contentType:"image/svg+xml",body:'<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160"><circle cx="80" cy="80" r="60" fill="#73d2ff"/></svg>'}));
    await page.goto(preview);
    await page.locator(".live-draft-board").waitFor();
    const originalWidth=(await page.locator(".content").boundingBox()).width;
    await page.locator("#sidebar-toggle").click();
    assert.equal(await page.locator("#league-sidebar").isVisible(),false);
    assert.equal(await page.locator("#sidebar-open").getAttribute("aria-expanded"),"false");
    if(width>800)assert.ok((await page.locator(".content").boundingBox()).width>originalWidth+200);
    await page.reload();await page.locator(".live-draft-board").waitFor();
    assert.equal(await page.locator("#league-sidebar").isVisible(),false);
    await page.locator("#sidebar-open").click();
    assert.equal(await page.locator("#league-sidebar").isVisible(),true);

    const garchomp=page.locator('.draft-board-pokemon[data-pokemon-name="Garchomp"]');
    await garchomp.click();
    const detail=page.locator(".pokemon-detail-overlay");
    await detail.locator("h2").waitFor();
    assert.equal(await detail.locator(".detail-badges span").first().innerText(),"$18");
    await detail.locator(".detail-close").click();

    const slider=page.locator(".draft-board-position"),scroller=page.locator(".draft-board-scroll");
    assert.equal(await slider.isVisible(),true);
    await slider.evaluate(input=>{input.value=String(Number(input.max)/2);input.dispatchEvent(new Event("input",{bubbles:true}));});
    assert.ok(await scroller.evaluate(node=>node.scrollLeft>100));
    await scroller.evaluate(node=>{node.scrollTop=500;});
    const header=await page.locator(".draft-snake-table thead th").nth(2).boundingBox();
    const bounds=await scroller.boundingBox();
    assert.ok(Math.abs(header.y-bounds.y)<15,"Headers stick to the board viewport");
    await scroller.evaluate(node=>{node.scrollTop=0;node.scrollLeft=0;});
    const fit=await garchomp.evaluate(card=>{
      const cell=card.closest("td").getBoundingClientRect(),box=card.getBoundingClientRect(),name=card.querySelector("strong");
      const range=document.createRange();range.selectNodeContents(name);
      return {inside:box.bottom<=cell.bottom+1&&box.right<=cell.right+1,lines:range.getClientRects().length,overflow:card.scrollHeight>card.clientHeight+1};
    });
    assert.equal(fit.inside,true);assert.equal(fit.lines,1,"Garchomp should fit on one line");assert.equal(fit.overflow,false);

    await page.locator('#draft-grid [data-pokemon-name="Meowscarada"]').click();
    await detail.locator(".detail-close").click();
    assert.match(await page.locator("#draft-action-selection").innerText(),/Meowscarada.*\$14/);
    assert.equal(await page.locator("#make-pick").isEnabled(),true);
    await page.locator("#make-pick").click();
    await page.locator('.draft-board-pokemon[data-pokemon-name="Meowscarada"]').waitFor();
    await page.locator("#preview-role").selectOption("GUEST");
    await page.waitForFunction(()=>state.user.role==="GUEST");
    await page.locator('#draft-grid [data-pokemon-name="Corviknight"]').click();
    await detail.locator(".detail-close").click();
    assert.equal(await page.locator("#make-pick").isDisabled(),true);
    assert.equal(await page.locator("#manager-nav").isVisible(),false);
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
    assert.deepEqual(errors,[]);
    await page.screenshot({path:new URL(`../target/draft-layout-${width}.png`,import.meta.url).pathname,fullPage:true});
    await context.close();
    console.log(`PASS: ${width}px collapse, selection, prices, sticky headers, persistent scrolling, card fit and guest permissions`);
  }
} finally {await browser.close();}
