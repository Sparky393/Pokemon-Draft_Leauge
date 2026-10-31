/* Read-only, client-side PNG export of the complete Draft Room board. */
(() => {
  const CELL_WIDTH = 194;
  const ROW_HEIGHT = 104;
  const LABEL_WIDTH = 82;
  const HEADER_HEIGHT = 110;
  const MARGIN = 28;
  const TITLE_HEIGHT = 78;

  function text(ctx, value, x, y, maxWidth) {
    ctx.fillText(String(value ?? ""), x, y, maxWidth);
  }

  function wrapped(ctx, value, x, y, width, lineHeight, maxLines = 2) {
    const words = String(value ?? "").split(/\\s+/);
    const lines = [];
    let line = "";
    for (const word of words) {
      const candidate = line ? line + " " + word : word;
      if (ctx.measureText(candidate).width > width && line) {
        lines.push(line);
        line = word;
      } else {
        line = candidate;
      }
    }
    if (line) lines.push(line);
    lines.slice(0, maxLines).forEach((part, i) => text(ctx, part, x, y + i * lineHeight, width));
  }

  async function downloadFullDraftBoard(snapshot) {
    if (!snapshot || !Array.isArray(snapshot.picks) || !Array.isArray(state.teams)) {
      throw new Error("Draft board data is unavailable.");
    }
    const teams = [...state.teams].sort((a, b) => Number(a.draftPosition) - Number(b.draftPosition));
    if (!teams.length) throw new Error("No draft teams are available.");
    const rounds = 10;
    const width = MARGIN * 2 + LABEL_WIDTH + teams.length * CELL_WIDTH;
    const height = MARGIN * 2 + TITLE_HEIGHT + HEADER_HEIGHT + rounds * ROW_HEIGHT + 30;
    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext("2d");
    if (!ctx) throw new Error("Your browser does not support image export.");

    ctx.fillStyle = "#0c1b2d";
    ctx.fillRect(0, 0, width, height);
    ctx.fillStyle = "#ffffff";
    ctx.font = "bold 29px system-ui, sans-serif";
    text(ctx, "Full Draft Board", MARGIN, MARGIN + 32);
    ctx.fillStyle = "#bed5ec";
    ctx.font = "16px system-ui, sans-serif";
    text(ctx, `${snapshot.picks.length} / ${rounds * teams.length} picks · ${snapshot.settings?.snakeDraft ? "Snake" : "Linear"} draft`, MARGIN, MARGIN + 60);

    const top = MARGIN + TITLE_HEIGHT;
    const left = MARGIN + LABEL_WIDTH;
    ctx.fillStyle = "#183652";
    ctx.fillRect(MARGIN, top, LABEL_WIDTH + teams.length * CELL_WIDTH, HEADER_HEIGHT);
    ctx.fillStyle = "#e8f4ff";
    ctx.font = "bold 15px system-ui, sans-serif";
    text(ctx, "Round", MARGIN + 10, top + 35, LABEL_WIDTH - 16);

    for (let col = 0; col < teams.length; col++) {
      const team = teams[col];
      const x = left + col * CELL_WIDTH;
      ctx.strokeStyle = "#355773";
      ctx.strokeRect(x, top, CELL_WIDTH, HEADER_HEIGHT);
      ctx.fillStyle = "#ffffff";
      ctx.font = "bold 15px system-ui, sans-serif";
      wrapped(ctx, team.name, x + 10, top + 27, CELL_WIDTH - 20, 20, 3);
      ctx.fillStyle = "#a8c6e2";
      ctx.font = "13px system-ui, sans-serif";
      text(ctx, `Draft position #${team.draftPosition}`, x + 10, top + 92, CELL_WIDTH - 20);
    }

    const picks = new Map(snapshot.picks.map(p => [Number(p.overallPick), p]));
    for (let row = 0; row < rounds; row++) {
      const round = row + 1;
      const y = top + HEADER_HEIGHT + row * ROW_HEIGHT;
      const reversed = !!snapshot.settings?.snakeDraft && round % 2 === 0;
      ctx.fillStyle = "#17334e";
      ctx.fillRect(MARGIN, y, LABEL_WIDTH, ROW_HEIGHT);
      ctx.strokeStyle = "#355773";
      ctx.strokeRect(MARGIN, y, LABEL_WIDTH, ROW_HEIGHT);
      ctx.fillStyle = "#ffffff";
      ctx.font = "bold 19px system-ui, sans-serif";
      text(ctx, `R${round}`, MARGIN + 12, y + 39);
      ctx.font = "17px system-ui, sans-serif";
      text(ctx, reversed ? "←" : "→", MARGIN + 12, y + 66);

      for (let col = 0; col < teams.length; col++) {
        const overall = row * teams.length + (reversed ? teams.length - col : col + 1);
        const pick = picks.get(overall);
        const x = left + col * CELL_WIDTH;
        ctx.fillStyle = pick ? "#244461" : (row % 2 ? "#10283e" : "#142e46");
        ctx.fillRect(x, y, CELL_WIDTH, ROW_HEIGHT);
        ctx.strokeStyle = "#355773";
        ctx.strokeRect(x, y, CELL_WIDTH, ROW_HEIGHT);
        ctx.fillStyle = "#b9d0e5";
        ctx.font = "12px system-ui, sans-serif";
        text(ctx, `Pick ${overall}`, x + 10, y + 20);
        if (pick) {
          ctx.fillStyle = "#ffffff";
          ctx.font = "bold 16px system-ui, sans-serif";
          wrapped(ctx, pick.pokemonName, x + 10, y + 45, CELL_WIDTH - 20, 19, 2);
          ctx.fillStyle = "#9de6bc";
          ctx.font = "bold 14px system-ui, sans-serif";
          text(ctx, typeof pick.price === "number" ? `$${pick.price}` : "Price unavailable", x + 10, y + 91, CELL_WIDTH - 20);
        } else {
          ctx.fillStyle = "#839db4";
          ctx.font = "15px system-ui, sans-serif";
          text(ctx, "—", x + 10, y + 60);
        }
      }
    }

    ctx.fillStyle = "#9bb6ce";
    ctx.font = "12px system-ui, sans-serif";
    text(ctx, "Generated from the current Draft Room view · No league data changed", MARGIN, height - 13);

    const blob = await new Promise(resolve => canvas.toBlob(resolve, "image/png"));
    if (!blob) throw new Error("Could not create the draft board image.");
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "full-draft-board.png";
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  }

  window.downloadFullDraftBoard = downloadFullDraftBoard;
})();
