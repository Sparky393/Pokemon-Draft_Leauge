/* Password management: self-service changes and commissioner resets. */
async function renderAccount() {
  const manager = state.user.role === "MANAGER";
  const security = await api("/api/auth/security");
  const passwordWarning = security.usingInitialPassword
    ? '<div class="card form-error" role="status"><strong>Change your initial password.</strong> '
      + 'This account still uses the original setup password. Use the Change My Password form below before the league begins.</div>'
    : "";
  const myTeam = state.teams.find(t => Number(t.id) === Number(state.user.teamId));
  const branding = !manager && myTeam ? `
    <section class="card branding-card" aria-labelledby="team-branding-heading">
      <div class="eyebrow">Club Identity</div>
      <div class="branding-intro">
        ${teamAvatar(myTeam, "lg")}
        <div><h2 id="team-branding-heading">Customize Your Team</h2>
          <p class="muted">Your name and crest appear across the whole league.</p></div>
      </div>
      <div class="branding-fields">
        <div>
          <h3>Team Name</h3>
          <p class="muted">Make it your own. Names must be unique across the league.</p>
          <div class="branding-name-form">
            <label>Display name<input id="branding-name" maxlength="40" minlength="2" autocomplete="off" value="${esc(myTeam.name)}"></label>
            <button class="primary" id="branding-save-name" type="button">Save Name</button>
          </div>
          <p class="muted branding-help">2–40 characters · Changing this does not affect your roster, results, or league schedule.</p>
        </div>
        <div>
          <h3>Team Logo</h3>
          <p class="muted">A square picture works best for schedules and standings.</p>
          <label class="branding-logo-picker" for="branding-file">
            <span id="branding-preview">${teamAvatar(myTeam, "lg")}</span>
            <span><strong>Choose your crest</strong><small>Click to select a PNG, JPG, or GIF · max 1 MB</small></span>
            <input id="branding-file" type="file" accept="image/png,image/jpeg,image/gif">
          </label>
          <div class="branding-logo-actions">
            <button class="primary" type="button" id="branding-upload" disabled>Upload Logo</button>
            <button class="secondary" type="button" id="branding-remove" ${myTeam.logoUrl ? "" : "disabled"}>Remove Logo</button>
          </div>
          <p class="muted branding-help" id="branding-selected" aria-live="polite">Your logo is stored with your team, even after a deployment.</p>
        </div>
      </div>
    </section>` : "";
  $("#page-account").innerHTML = `
    <h1>Account Settings</h1>
    ${branding}
    ${passwordWarning}
    <div class="card">
      <h2>Change My Password</h2>
      <p class="muted">Signed in as ${esc(state.user.username)}. Use 8–72 characters.</p>
      <div class="toolbar">
        <label>Current password<input id="account-current" type="password" autocomplete="current-password"></label>
        <label>New password<input id="account-new" type="password" minlength="8" maxlength="72" autocomplete="new-password"></label>
        <label>Confirm new password<input id="account-confirm" type="password" minlength="8" maxlength="72" autocomplete="new-password"></label>
        <button class="primary" id="account-change">Change Password</button>
      </div>
    </div>
    ${manager ? '<div class="card"><h2>Reset Coach Password</h2><p class="muted">Commissioner only. Generates a temporary password for the selected coach. Share it privately; the coach can change it after signing in.</p><div class="toolbar"><label>Coach<select id="reset-user"><option value="">Select coach</option></select></label><button class="danger" id="reset-password">Generate Temporary Password</button></div></div>' : ""}`;
  if (branding) {
    $("#branding-save-name").onclick = async () => {
      const button = $("#branding-save-name");
      const name = $("#branding-name").value.trim();
      if (name.length < 2 || name.length > 40) return toast("Team names must be 2–40 characters", true);
      button.disabled = true;
      try {
        const updated = await api("/api/teams/me/name", {method:"PUT", body:{name}});
        refreshTeamIdentity(updated);
        toast("Team name saved across the league");
        await renderAccount();
      } catch (error) { toast(error.message, true); }
      finally { button.disabled = false; }
    };

    let previewUrl = null;
    $("#branding-file").onchange = () => {
      const file = $("#branding-file").files[0];
      $("#branding-upload").disabled = !file || file.size > 1024 * 1024;
      $("#branding-selected").textContent = !file ? "No file selected."
        : file.size > 1024 * 1024 ? "Image is too large. Max size: 1 MB."
        : file.name + " · Ready to upload";
      if (previewUrl) URL.revokeObjectURL(previewUrl);
      if (file && file.size <= 1024 * 1024) {
        previewUrl = URL.createObjectURL(file);
        const img = document.createElement("img");
        img.src = previewUrl;
        img.alt = "Selected team logo preview";
        const frame = document.createElement("span");
        frame.className = "team-avatar team-avatar--lg";
        frame.appendChild(img);
        $("#branding-preview").replaceChildren(frame);
      }
    };
    $("#branding-upload").onclick = async () => {
      const file = $("#branding-file").files[0];
      if (!file) return toast("Choose an image first", true);
      if (file.size > 1024 * 1024) return toast("Maximum size is 1 MB", true);
      const button = $("#branding-upload");
      button.disabled = true;
      try {
        const body = new FormData();
        body.append("file", file);
        const response = await fetch("/api/teams/me/logo", {method:"POST", body, headers: {"X-Requested-With":"XMLHttpRequest"}});
        const result = await response.json();
        if (!response.ok) throw new Error(apiErrorMessage(response.status, result));
        refreshTeamIdentity(result);
        toast("Team logo uploaded");
        await renderAccount();
      } catch (error) { toast(error.message, true); button.disabled = false; }
      finally { if (previewUrl) URL.revokeObjectURL(previewUrl); }
    };
    $("#branding-remove").onclick = async () => {
      if (!confirm("Remove your current team logo?")) return;
      try {
        const updated = await api("/api/teams/me/logo", {method:"DELETE"});
        refreshTeamIdentity(updated);
        toast("Team logo removed");
        await renderAccount();
      } catch (error) { toast(error.message, true); }
    };
  }

  $("#account-change").addEventListener("click", async () => {
    const currentPassword = $("#account-current").value;
    const newPassword = $("#account-new").value;
    const confirmation = $("#account-confirm").value;
    if (newPassword !== confirmation) return toast("New passwords do not match", true);
    if (newPassword.length < 8 || newPassword.length > 72) return toast("Use 8–72 characters", true);
    try {
      await api("/api/auth/change-password", {method: "POST", body: {currentPassword, newPassword}});
      $("#account-current").value = "";
      $("#account-new").value = "";
      $("#account-confirm").value = "";
      toast("Password changed");
    } catch (e) { toast(e.message, true); }
  });
  if (manager) {
    const users = await api("/api/auth/manager/users");
    $("#reset-user").innerHTML += users.map(u => `<option value="${u.id}">${esc(u.username)}</option>`).join("");
    $("#reset-password").addEventListener("click", async () => {
      const id = Number($("#reset-user").value);
      if (!id) return toast("Select a coach", true);
      const name = users.find(u => u.id === id)?.username || "this coach";
      if (!confirm(`Generate a new temporary password for ${name}? Their old password will immediately stop working.`)) return;
      try {
        const result = await api(`/api/auth/manager/users/${id}/reset-password`, {method: "POST"});
        const secret = result?.temporaryPassword;
        if (!secret) throw new Error("No temporary password returned. Try again.");
        // Shown once; it is never stored in localStorage or included in a URL.
        document.querySelector("#manager-temp-password")?.remove();
        const output = document.createElement("div");
        output.id = "manager-temp-password";
        output.className = "card";
        const heading = document.createElement("strong");
        heading.textContent = "New temporary password for " + name;
        const code = document.createElement("code");
        code.textContent = secret;
        code.style.cssText = "display:block;user-select:all;overflow-wrap:anywhere;margin:12px 0;font-size:16px;";
        const info = document.createElement("p");
        info.textContent = "Give this privately to the coach and ask them to change it immediately. Copy it now; you cannot retrieve it again.";
        const close = document.createElement("button");
        close.type = "button"; close.textContent = "Hide password"; close.onclick = () => output.remove();
        output.append(heading,code,info,close);
        $("#reset-password").insertAdjacentElement("afterend", output);
        toast(`New temporary password generated for ${name}`);
      } catch (e) { toast(e.message, true); }
    });
  }
}
