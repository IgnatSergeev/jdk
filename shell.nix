with import <nixpkgs> {};
(pkgs.buildFHSEnv {
    name = "jdk";
    targetPkgs = pkgs: ([ 
      # Lsp, treesitter-parsers and debugger
      pkgs.clang-tools 
      pkgs.vimPlugins.nvim-treesitter-parsers.cmake 
      pkgs.vimPlugins.nvim-treesitter-parsers.cpp
      pkgs.vimPlugins.nvim-treesitter-parsers.c 
      pkgs.vimPlugins.nvim-treesitter-parsers.yaml
      pkgs.vimPlugins.nvim-treesitter-parsers.json
      pkgs.vimPlugins.nvim-treesitter-parsers.markdown
      pkgs.vimPlugins.nvim-treesitter-parsers.markdown_inline
      pkgs.vimPlugins.nvim-treesitter-parsers.nix
      pkgs.gdb

      # Build tools
      pkgs.clang
      pkgs.bear
      pkgs.libz
      pkgs.lld
      pkgs.autoconf
      pkgs.alsa-lib
      pkgs.alsa-lib.dev
      pkgs.cups
      pkgs.cups.dev
      pkgs.fontconfig
      pkgs.fontconfig.dev
      pkgs.freetype
      pkgs.freetype.dev

      pkgs.zlib
      pkgs.zlib.dev
      pkgs.xorg.libX11
      pkgs.xorg.libX11.dev
      pkgs.xorg.libXext
      pkgs.xorg.libXext.dev
      pkgs.xorg.libXrender
      pkgs.xorg.libXrender.dev
      pkgs.xorg.xorgproto
      pkgs.xorg.libXrandr
      pkgs.xorg.libXrandr.dev
      pkgs.xorg.libXtst
      pkgs.xorg.libXi
      pkgs.xorg.libXi.dev
      pkgs.xorg.libXt
      pkgs.xorg.libXt.dev
    ]);
    runScript = "zsh";
}).env
